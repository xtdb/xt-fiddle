(ns xt-play.transactions
  (:require [clojure.string :as str]
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

(defn- run! [node tx-type tx-batches query]
  (let [tx-batches (->> tx-batches
                        (map #(update % :system-time util/format-system-time))
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
    (util/map-results->rows res)))

(defn- run!-with-jdbc-conn [tx-batches query]
  (with-open [conn (jdbc/get-connection config/db)]
    (doseq [tx (prepare-statements tx-batches)
            statement tx]
      (log/info "beta executing statement:" statement)
      (jdbc/execute! conn statement))
    (log/info "beta running query:" query)
    (let [res (jdbc/execute! conn [query] {:builder-fn jdbc-res/as-arrays})]
      (log/info "beta query resoponse" res)
      res)))

(defn run!!
  "Given transaction batches, a query and the type of transaction to
  use, will run transaction batches and queries sequentially,
  returning the last query response."
  [{:keys [tx-batches query tx-type]}]
  (let [query (if (= "xtql" tx-type) (util/read-edn query) query)]
    (try
      (with-open [node (xtn/start-node {})]
        (if (= "sql-beta" tx-type)
          (run!-with-jdbc-conn tx-batches query)
          (run! node tx-type tx-batches query)))
      (catch Exception e
        (log/warn :submit-error {:e e})
        (throw e)))))
