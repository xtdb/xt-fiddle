(ns xt-play.transactions
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.instant :refer [read-instant-date]]
            [clojure.tools.logging :as log]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as jdbc-res]
            [xt-play.config :as config]
            [xt-play.util :as util]
            [xtdb.api :as xt]
            [xtdb.node :as xtn]))

(defn- encode-txs [tx-type txs]
  (case (keyword tx-type)
    :sql (->> (str/split txs #";")
              (remove str/blank?)
              (map #(do [:sql %]))
              (vec))
    :xtql (util/read-edn (str "[" txs "]"))
    ;;else
    txs))

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

(defn format-system-time [s]
  (when s (read-instant-date s)))

(defn- run!-tx [node tx-type tx-batches query]
  (let [tx-batches (->> tx-batches
                        (map #(update % :system-time format-system-time))
                        (map #(update % :txs (partial encode-txs tx-type))))]
    (doseq [{:keys [system-time txs] :as batch} tx-batches]
      (log/info tx-type "running batch: " batch)
      (let [tx (xt/submit-tx node txs {:system-time system-time})
            results (xt/q node '(from :xt/txs [{:xt/id $tx-id} xt/error])
                          {:args {:tx-id (:tx-id tx)}})]
        ;; If any transaction fails, throw the error
        (log/info tx-type "batch complete:" tx ", results:" results)
        (when-let [error (-> results first :xt/error)]
          (throw error)))))
  (log/info tx-type "running query:" query)
  (let [res (xt/q node query (when (string? query)
                               {:key-fn :snake-case-string}))]
    (log/info tx-type "XTDB query response:" res)
    res))

(defn- PGobject->clj [v]
  (if (= org.postgresql.util.PGobject (type v))
    (json/read-str (.getValue v) :key-fn keyword)
    v))

(defn- parse-result [result]
  ;; TODO - this shouldn't be needed, a fix is on the way in
  ;;        a later version of xtdb-jdb
  ;; This will only pick up top level objects
  (mapv
   (fn [row]
     (mapv PGobject->clj row))
   result))

(defn- run!-with-jdbc-conn [tx-batches query]
  (with-open [conn (jdbc/get-connection config/db)]
    (doseq [tx (prepare-statements tx-batches)
            statement tx]
      (log/info "beta executing statement:" statement)
      (jdbc/execute! conn statement))
    (log/info "beta running query:" query)
    (let [res (jdbc/execute! conn [query] {:builder-fn jdbc-res/as-arrays})]
      (log/info "beta query resoponse" res)
      (parse-result res))))

(defn run!!
  "Given transaction batches, a query and the type of transaction to
  use, will run transaction batches and queries sequentially,
  returning the last query response in column format."
  [{:keys [tx-batches query tx-type]}]
  (let [query (if (= "xtql" tx-type) (util/read-edn query) query)]
    (try
      (with-open [node (xtn/start-node {})]
        (if (= "sql-beta" tx-type)
          (run!-with-jdbc-conn tx-batches query)
          (util/map-results->rows
           (run!-tx node tx-type tx-batches query))))
      (catch Exception e
        (log/warn :submit-error {:e e})
        (throw e)))))

(defn docs-run!!
  "Given transaction batches and a query from the docs, will return the query
  response in map format. Assumes tx type is sql."
  [{:keys [tx-batches query]}]
  (try
    (with-open [node (xtn/start-node {})]
      (run!-tx node "sql" tx-batches query))
    (catch Exception e
      (log/warn :submit-error {:e e})
      (throw e))))

(comment
  (with-open [node (xtn/start-node {})]
    (doseq [st [#inst "2022" #inst "2021"]]
      (let [tx (xt/submit-tx node [] {:system-time st})
            results (xt/q node '(from :xt/txs [{:xt/id $tx-id} xt/error])
                          {:basis {:at-tx tx}
                           :args {:tx-id (:tx-id tx)}})]
        (when-let [error (-> results first :xt/error)]
          (throw (ex-info "Transaction error" {:error error})))))))
