(ns xt-play.tx-batch
  (:refer-clojure :exclude [list])
  (:require [re-frame.core :as rf]))

;; TODO: TESTS

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

;; >> Utils

(defn- new-id! []
  (->> (gensym "tx") name (keyword 'xt-play.tx-batch)))



;; >> Events

(rf/reg-event-db ::init
  (fn [db [_ initial-value]]
    (let [ids (->> (repeatedly new-id!) (take (count initial-value)) vec)]
      (-> db
          (assoc ::list ids)
          (assoc ::id->batch (zipmap ids initial-value))))))

(rf/reg-event-db ::append
  (fn [db [_ tx-batch]]
    (let [id (new-id!)]
      (-> db
          (update ::list conj id)
          (update ::id->batch assoc id tx-batch)))))

(rf/reg-event-db ::delete
  (fn [db [_ id]]
    (println "delete" id)
    (-> db
        (update ::list #(->> % (remove (fn [x] (= x id))) vec))
        (update ::id->batch dissoc id))))

(rf/reg-event-db ::update
  (fn [db [_ id f]]
    (update-in db [::id->batch id] f)))

(rf/reg-event-db ::assoc
  (fn [db [_ id k txs]]
    (assoc-in db [::id->batch id k] txs)))



;; >> Subscriptions

(rf/reg-sub ::id-batch-pairs
  (fn [db _]
    (let [id->batch (::id->batch db)
          lst (::list db)]
      (->> lst
           (mapv (fn [id] [id (id->batch id)]))))))



;; >> API

(defn list
  "Given a db return the list of batches in the correct order."
  [db]
  (let [ids (::list db)
        id->batch (::id->batch db)]
    (mapv id->batch ids)))

(def blank {:txs "" :system-time nil})

(def default-dml "[:put-docs :docs {:xt/id 1 :foo \"bar\"}]")
(def default-sql-insert "INSERT INTO docs (_id, col1) VALUES (1, 'foo');
INSERT INTO docs RECORDS {_id: 2, col1: 'bar', col2:' baz'};")
(defn default [type]
  {:system-time nil
   :txs (case type
          :xtql
          default-dml
          ;;default
          default-sql-insert)})

(defn param-encode [tx-batches]
  (-> tx-batches clj->js js/JSON.stringify js/btoa))

;; TODO: Special case existing txs
(defn param-decode [s]
  (let [txs (-> s js/atob js/JSON.parse (js->clj :keywordize-keys true))]
    (->> txs
         (map #(update % :system-time (fn [d] (when d (js/Date. d))))))))
