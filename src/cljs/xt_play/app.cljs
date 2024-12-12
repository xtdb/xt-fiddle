(ns xt-play.app
  (:require [day8.re-frame.http-fx] ;; don't delete
            [lambdaisland.glogi :as log]
            [lambdaisland.glogi.console :as glogi-console]
            [re-frame.core :as rf]
            [reagent.dom :as r-dom]
            [xt-play.components.highlight :as hl]
            [xt-play.model.query :as query]
            [xt-play.model.query-params :as query-params]
            [xt-play.model.tx-batch :as tx-batch]
            [xt-play.view :as view]
            ["lz-string" :as lz-string]))

(glogi-console/install!)

(log/set-levels
 {:glogi/root   :info})    ;; Set a root logger level, this will be inherited by all loggers

;; 'my.app.thing :trace  ;; Some namespaces you might want detailed logging

(defn- decompress-params [{:keys [uri-version] :as query-params}]
  (let [decompress (case uri-version
                     "1" lz-string/decompressFromEncodedURIComponent
                     ;; default
                     js/atob)
        maybe-decompress #(when % (decompress %))]
    (-> query-params
        (update :txs maybe-decompress)
        (update :query maybe-decompress))))

;; TODO: Special case existing txs
(defn- decode-txs [s]
  (let [txs (-> s js/JSON.parse (js->clj :keywordize-keys true))]
    (->> txs
         (map #(update % :system-time (fn [d] (when d (js/Date. d))))))))

(rf/reg-event-fx
  ::init
  [(rf/inject-cofx ::query-params/get)]
  (fn [{:keys [query-params]} [_ xt-version]]
    (let [{:keys [type txs query]} (decompress-params query-params)
          type (if type (keyword type) :sql)]
      {:db {:version xt-version
            :type type
            :query (or query (query/default type))}
       :dispatch [::tx-batch/init
                  (if txs
                    (decode-txs txs)
                    [(tx-batch/default type)])]})))

(defn ^:dev/after-load start! []
  (log/info :start "start")
  (hl/setup)
  (rf/dispatch-sync [::init js/xt_version])
  (r-dom/render [view/app] (js/document.getElementById "app")))

(defn ^:export init []
  ;; init is called ONCE when the page loads
  ;; this is called in the index.html and must be exported
  ;; so it is available even in :advanced release builds
  (log/info :init "init")
  (start!))

;; this is called before any code is reloaded
(defn ^:dev/before-load stop []
  (log/info :stop "stop"))
