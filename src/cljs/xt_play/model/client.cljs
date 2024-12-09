(ns xt-play.model.client
  (:require [re-frame.core :as rf]
            [xt-play.config :as config]
            [xt-play.model.clipboard :as clipboard]
            [xt-play.model.href :as href]
            [xt-play.model.query :as query]
            [xt-play.model.query-params :as query-params]
            [xt-play.model.tx-batch :as tx-batch]))

(rf/reg-event-db
  :hide-copy-tick
  (fn [db _]
    (dissoc db :copy-tick)))

(rf/reg-event-fx
  :copy-url
  [(rf/inject-cofx ::href/get)]
  (fn [{:keys [db href]} _]
    {::clipboard/set {:text href}
     :db (assoc db :copy-tick true)
     :dispatch-later {:ms 800 :dispatch [:hide-copy-tick]}}))

(rf/reg-sub
  :copy-tick
  :-> :copy-tick)

(defn- param-encode [tx-batches]
  (-> tx-batches clj->js js/JSON.stringify js/btoa))

(rf/reg-event-fx
 :update-url
 (fn [{:keys [db]} _]
   {::query-params/set {:version (:version db)
                        :type (name (:type db))
                        :txs (param-encode (tx-batch/batch-list db))
                        :query (js/btoa (:query db))}}))

(rf/reg-event-fx
  :dropdown-selection
  (fn [{:keys [db]} [_ new-type]]
    {:db (-> db
             (assoc :type new-type)
             (assoc :query (query/default new-type)))
     :fx [[:dispatch [::tx-batch/init [(tx-batch/default new-type)]]]
          [:dispatch [:update-url]]]}))

(rf/reg-event-db
  :set-query
  (fn [db [_ query]]
    (assoc db :query query)))

(rf/reg-event-fx
  :fx ;; todo, use explicit reg-event-fxs
  (fn [_ [_ effects]]
    {:fx effects}))

(rf/reg-sub
  :get-type
  :-> :type)

(rf/reg-sub
  :query
  :-> :query)

(rf/reg-sub
  :version
  :-> :version)

(def items
  (vec
   (keep (fn [tx-type]
           (when (or (:show-beta? config/config)
                     (not (:beta? tx-type)))
             (select-keys tx-type [:value :label])))
         (vals config/tx-types))))
