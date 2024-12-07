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

(def tx-types
  {:sql "SQL"
   :xtql "XTQL"
   :sql-beta "Beta"})

(def beta-copy
  (str "We are currently testing a new SQL framework for XTDB Play which utilises more of XTDB 2.0s powerful new features. "
       "Feel free to stick arround and have a play, but if you want to return to safty, select a different mode from the dropdown"))

(def config
  {:show-beta? true})
