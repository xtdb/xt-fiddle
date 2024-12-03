(ns xt-play.run
  (:require [xt-play.tx-batch :as tx-batch]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [ajax.core :as ajax]))

(defn- to-iso-str [d] (when d (.toISOString d)))

(defn- db-run-opts [{:keys [query type] :as db}]
  (let [params {:tx-type type
                :query query
                :tx-batches
                (->> (tx-batch/list db)
                     (map #(update % :system-time to-iso-str)))}]
    {:method :post
     :uri "/db-run"
     :params params
     :timeout 3000
     :format (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success [::request-success]
     :on-failure [::request-failure]}))

(rf/reg-event-fx
 ::run
 (fn [{:keys [db]}]
   {:db (-> db
            (assoc ::loading? true)
            (dissoc ::response?)
            (dissoc ::failure ::results))
    :http-xhrio (db-run-opts db)}))

(rf/reg-event-db
 ::request-success
  (fn [db [_ results]]
    (-> db
        (dissoc ::loading?)
        (assoc ::response? true)
        (assoc ::results results))))

(rf/reg-event-db ::request-failure
  (fn [db [_ {:keys [response] :as _failure-map}]]
    (-> db
        (dissoc ::loading?)
        (assoc ::failure response))))

(rf/reg-sub ::results-or-failure
  :-> #(let [results (select-keys % [::results ::failure ::response?])]
         (when-not (empty? results)
           results)))

(rf/reg-sub ::results?
  :<- [::results-or-failure]
  :-> boolean)

(rf/reg-sub ::loading?
  :-> ::loading?)

(comment
  (require '[re-frame.db :as db])
  (def db @db/app-db)
  (keys db))

