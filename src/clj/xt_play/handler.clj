(ns xt-play.handler
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [muuntaja.core :as m]
            [reitit.coercion.spec :as rcs]
            [reitit.dev.pretty :as pretty]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :as params]
            [ring.util.response :as response]
            [xt-play.transactions :as txs]
            [xt-play.view :as view]))

;; TODO:
;; [x] Send tx data back asis - data manipulation server side
;; [x] Beta is an option in the type
;; [x] Handle multi tx on pgwire
;; [x] Handle system time on pgwire
;; [x] Manipulate response data server side
;; [x] remove btn
;; [x] banner
;; [x] logging
;; [x] handle todos
;; [x] Tests!
;; [x] Refactor - split into more meaningful files
;; [x] - add config, request, response, xt ns
;; [x] - split out ui to components
;; [x] - Better management on subs
;; [x] cljs tests
;; [] Handle queries in tx?
;; [] Display errors in result box
;; [] automated test runners / pipelines
;; [] extract common tailwind classes, e.g. icon sizes, to standardize

(s/def ::system-time (s/nilable string?))
(s/def ::txs string?)
(s/def ::tx-batches (s/coll-of (s/keys :req-un [::system-time ::txs])))
(s/def ::query string?)
(s/def ::tx-type #{"sql-beta" "xtql" "sql"})
(s/def ::db-run (s/keys :req-un [::tx-batches ::query]))
(s/def ::beta-db-run (s/keys :req-un [::tx-batches ::query ::tx-type]))

(defn- handle-client-error [ex _]
  {:status 400
   :body {:message (ex-message ex)
          :exception (.getClass ex)
          :data (ex-data ex)}})

(defn- handle-other-error [ex _]
  {:status 500
   :body {:message (ex-message ex)
          :exception (.getClass ex)
          :data (ex-data ex)}})

(def exception-middleware
  (exception/create-exception-middleware
   (merge
    exception/default-handlers
    {xtdb.IllegalArgumentException handle-client-error
     xtdb.RuntimeException handle-client-error
     clojure.lang.ExceptionInfo handle-client-error
     ::exception/default handle-other-error})))

(defn run-handler [{{body :body} :parameters :as request}]
  (log/debug "run-handler" request)
  (log/info :db-run body)
  (if-let [result (txs/run!! body)]
    {:status 200
     :body result}
    {:status 400}))

(defn docs-run-handler [{{body :body} :parameters :as request}]
  (log/debug "docs-run-handler" request)
  (log/info :docs-db-run body)
  (if-let [result (txs/docs-run!! body)]
    {:status 200
     :body result}
    {:status 400}))

(def routes
  (ring/router
   [["/"
     {:get {:summary "Fetch main page"
            :handler (fn [_request]
                       (-> (response/response view/index)
                           (response/content-type "text/html")))}}]

    ["/db-run" ;; if the contract for this changes, it'll break the docs, so
     ;; either docs need to change, or needs to remain backward
     ;; compatible
     {:post {:summary "Run transactions + a query"
             :parameters {:body ::db-run}
             :handler #'docs-run-handler}}]

    ["/beta-db-run"
     {:post {:summary "Run transactions + a query"
             :parameters {:body ::beta-db-run}
             :handler #'run-handler}}]

    ["/public/*" (ring/create-resource-handler)]]
   {:exception pretty/exception
    :data {:coercion rcs/coercion
           :muuntaja m/instance
           :middleware [#(wrap-cors %
                                    :access-control-allow-origin #".*"
                                    :access-control-allow-methods [:get :put :post :delete])
                        params/wrap-params
                        muuntaja/format-middleware
                        exception-middleware
                        rrc/coerce-exceptions-middleware
                        rrc/coerce-request-middleware
                        rrc/coerce-response-middleware]}}))

(def handler
  (ring/ring-handler
    routes
    (ring/routes (ring/create-default-handler))))

(defmethod ig/init-key ::handler [_ _opts]
  handler)
