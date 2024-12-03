(ns xt-play.handler
  (:require [clojure.edn :as edn]
            [clojure.instant :refer [read-instant-date]]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [hiccup.page :as h]
            [integrant.core :as ig]
            [muuntaja.core :as m]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as jdbc-res]
            [reitit.coercion.spec :as rcs]
            [reitit.dev.pretty :as pretty]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :as params]
            [ring.util.response :as response]
            [xt-play.util :as util]
            [xtdb.api :as xt]
            [xtdb.node :as xtn]))

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
;; [] Tests!
;; [] Refactor - split into more meaningful files
;; [] - add config, request, response, xt ns
;; [] - split out ui to components
;; [] - Better management on subs
;; [] Handle queries in tx?
;; [] Display errors in result box

(s/def ::system-time (s/nilable string?))
(s/def ::tx-batches (s/coll-of (s/keys :req-un [::system-time ::txs])))
(s/def ::query string?)
(s/def ::tx-type #{"sql-beta" "xtql" "sql"})
(s/def ::db-run (s/keys :req-un [::tx-batches ::query ::tx-type]))

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

(def xt-version
  (-> (slurp "deps.edn")
      (edn/read-string)
      (get-in [:deps 'com.xtdb/xtdb-core :mvn/version])))
(assert (string? xt-version) "xt-version not present")

(def index
  (h/html5
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:meta {:name "description" :content ""}]
    [:link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/default.min.css"}]
    [:link {:rel "stylesheet" :type "text/css" :href "/public/css/main.css"}]
    [:link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css"}]
    [:script {:src "https://cdn.tailwindcss.com"}]
    [:script {:async true
              :defer true
              :data-website-id "aabeabcb-ad76-47a4-9b4b-bef3fdc39af4"
              :src "https://bunseki.juxt.pro/umami.js"}]
    [:title "XTDB Play"]]
   [:body
    [:div {:id "app"}]
    [:script {:type "text/javascript" :src "/public/js/compiled/app.js"}]
    [:script {:type "text/javascript"}
     (str "var xt_version = '" xt-version "';")]
    [:script {:type "text/javascript"}
     "xt_play.app.init()"]]))

(def ^:private db
  {:dbtype "postgresql"
   :dbname "xtdb"
   :user "xtdb"
   :password "xtdb"
   :host "localhost"
   :port 5432})

(def ^:private read-edn (partial edn/read-string {:readers *data-readers*}))

(defn- convert-date [s] (when s (read-instant-date s)))

(defn- encode-txs [tx-type txs]
  (case (keyword tx-type)
    :sql (->> (str/split txs #";")
              (remove str/blank?)
              (map #(do [:sql %]))
              (vec))
    :xtql (read-edn (str "[" txs "]"))
    ;;else
    txs))

(defn- run! [node tx-type tx-batches query]
  (let [tx-batches (->> tx-batches
                        (map #(update % :system-time convert-date))
                        (map #(update % :txs (partial encode-txs tx-type))))]
    (doseq [{:keys [system-time txs] :as batch} tx-batches]
      (log/info tx-type "running batch: " batch)
      (let [tx (xt/submit-tx node txs {:system-time system-time})
            results (xt/q node '(from :xt/txs [{:xt/id $tx-id} xt/error])
                          {:args {:tx-id (:tx-id tx)}})]
        ;; If any transaction fails, throw the error
        ;; todo - send the error back to the server?
        (log/info tx-type "batch complete:" tx ", results:" results)
        (when-let [error (-> results first :xt/error)]
          (throw error)))))
  ;; Run query
  (log/info tx-type "running query:" query)
  (let [res (xt/q node query (when (string? query)
                               {:key-fn :snake-case-string}))]
    (log/info tx-type "XTDB query response:" res)
    {:status 200
     :body (util/map-results->rows res)}))

(defn- prepare-statements
  "Takes a batch of transactions and prepares the jdbc execution args to
  be run sequentially"
  [tx-batches]
  (for [{:keys [txs system-time]} tx-batches]
    (remove nil?
     [(when system-time
        [(format "BEGIN AT SYSTEM_TIME TIMESTAMP '%s'" system-time)])
      [txs]
      (when system-time
        ["COMMIT"])])))

(defn- run!-with-jdbc-conn [tx-batches query]
  (with-open [conn (jdbc/get-connection db)]
    (doseq [tx (prepare-statements tx-batches)
            statement tx]
      (log/info "beta executing statement:" statement)
      (jdbc/execute! conn statement))
    (log/info "beta running query:" query)
    (let [res (jdbc/execute! conn [query] {:builder-fn jdbc-res/as-arrays})]
      (log/info "beta query resoponse" res)
      {:status 200
       :body res})))

(defn run-handler [{{body :body} :parameters :as request}]
  (log/debug "run-handler" request)
  (let [{:keys [tx-batches query tx-type]} (get-in request [:parameters :body])
        query (if (= "xtql" tx-type) (read-edn query) query)]
    (log/info :db-run body)
    (try
      (with-open [node (xtn/start-node {})]
        (if (= "sql-beta" tx-type)
          (run!-with-jdbc-conn tx-batches query)
          (run! node tx-type tx-batches query)))
      (catch Exception e
        (log/warn :submit-error {:e e})
        (throw e)))))

(def routes
  (ring/router
   [["/"
     {:get {:summary "Fetch main page"
            :handler (fn [_request]
                       (-> (response/response index)
                           (response/content-type "text/html")))}}]

    ["/db-run"
     {:post {:summary "Run transactions + a query"
             :parameters {:body ::db-run}
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

(comment
  (with-open [node (xtn/start-node {})]
    (doseq [st [#inst "2022" #inst "2021"]]
      (let [tx (xt/submit-tx node [] {:system-time st})
            results (xt/q node '(from :xt/txs [{:xt/id $tx-id} xt/error])
                          {:basis {:at-tx tx}
                           :args {:tx-id (:tx-id tx)}})]
        (when-let [error (-> results first :xt/error)]
          (throw (ex-info "Transaction error" {:error error})))))))
