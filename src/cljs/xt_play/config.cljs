(ns xt-play.config)

(def ^:private default-dml
  "[:put-docs :docs {:xt/id 1 :foo \"bar\"}]")

(def ^:private default-sql-insert
  "INSERT INTO docs (_id, col1) VALUES (1, 'foo');
INSERT INTO docs RECORDS {_id: 2, col1: 'bar', col2:' baz'};")

(def default-transaction
  {:sql default-sql-insert
   :sql-beta default-sql-insert
   :xtql default-dml})

(def config
  {:show-beta? true})

(def tx-types
  {:sql {:value :sql
         :label "SQL"}
   :xtql {:value :xtql
          :label "XTQL"}
   :sql-beta {:value :sql-beta
              :label "Beta"
              :beta? true}})


