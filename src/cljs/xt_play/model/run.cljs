(ns xt-play.model.run
  (:require [ajax.core :as ajax]
            [re-frame.core :as rf]
            [xt-play.model.tx-batch :as tx-batch]))

(defn- db-run-opts [{:keys [query type] :as db}]
  (let [params {:tx-type type
                :query query
                :tx-batches (map #(update % :system-time (fn [d] (when d (.toISOString d))))
                                 (tx-batch/batch-list db))}]
    {:method :post
     :uri "/beta-db-run"
     :params params
     :timeout 3000
     :format (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success [::request-success]
     :on-failure [::request-failure]}))

(defn run [db]
  {:db (-> db
           (assoc ::loading? true)
           (dissoc ::failure ::results ::response?))
   :http-xhrio (db-run-opts db)})

(rf/reg-event-fx
 ::run
 (fn [{:keys [db]}]
   (merge (run db)
          {:dispatch [:update-url]})))

(rf/reg-event-db
 ::request-success
  (fn [db [_ results]]
    (-> db
        (dissoc ::loading?)
        (assoc ::response? true)
        (assoc ::results results))))

(rf/reg-event-db
 ::request-failure
  (fn [db [_ {:keys [response] :as _failure-map}]]
    (-> db
        (dissoc ::loading?)
        (assoc ::failure response))))

(rf/reg-sub
 ::results-or-failure
 (fn [db]
   (let [results (select-keys db [::results ::failure ::response?])]
     (when-not (empty? results)
       results))))

(rf/reg-sub
 ::results?
  :<- [::results-or-failure]
  :-> boolean)

(rf/reg-sub
 ::loading?
  :-> ::loading?)

(comment
  (require '[re-frame.db :as db])
  (def db @db/app-db)
  (keys db))
