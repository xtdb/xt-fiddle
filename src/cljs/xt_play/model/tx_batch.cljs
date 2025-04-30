(ns xt-play.model.tx-batch
  (:require [re-frame.core :as rf]
            [xt-play.config :as config]))

;; Goals:
;; - Batches are in a consistent order for rendering
;; - Batches are accessed via a consistent key
;;   - This means we can't just store a vector
;;   - This is required because `xt-play.editor` doesn't support
;;     updating the :source.
;;     This makes deleting difficult.
;;
;; Given the above we store:
;; - A map of id -> batch
;; - A vector of ids
;;
;; In practice this looks like:
;; {:tx1 <tx-batch>
;;  :tx2 <tx-batch>}
;; [:tx1 :tx2]
;; And as a user you'll mostly work with the map

(defn- new-id! []
  (->> (gensym "tx")
       name
       (keyword :xt-play.model.tx-batch)))

;; >> Events

(rf/reg-event-db
 ::init
 (fn [db [_ initial-value]]
   (let [ids (->> (repeatedly new-id!) (take (count initial-value)) vec)]
     (-> db
         (assoc ::list ids)
         (assoc ::id->batch (zipmap ids initial-value))))))

(rf/reg-event-db
 ::append
 (fn [db [_ tx-batch]]
   (let [id (new-id!)]
     (-> db
         (update ::list conj id)
         (update ::id->batch assoc id tx-batch)))))

(rf/reg-event-db
 ::delete
  (fn [db [_ id]]
    (println "delete" id)
    (-> db
        (update ::list #(->> % (remove (fn [x] (= x id))) vec))
        (update ::id->batch dissoc id))))

(rf/reg-event-db
 ::update
 (fn [db [_ id f]]
   (update-in db [::id->batch id] f)))

(rf/reg-event-db
 ::assoc
 (fn [db [_ id k txs]]
   (assoc-in db [::id->batch id k] txs)))

;; >> Subscriptions

(rf/reg-sub
 ::id-batch-pairs
 (fn [{batch-list ::list, batch-lookup ::id->batch} _]
   (mapv #(vector % (get batch-lookup %)) batch-list)))

;; >> API

(defn batch-list
  "Given a db return the list of batches in the correct order."
  [{batch-list ::list, batch-lookup ::id->batch}]
  (mapv #(get batch-lookup %) batch-list))

(def blank {:txs "" :system-time nil})

(defn default [tx-type]
  {:system-time nil
   :txs (config/default-transaction tx-type)})

(defn default-query [tx-type]
  {:txs (config/default-query tx-type)})
