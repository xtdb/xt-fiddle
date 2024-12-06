(ns xt-play.model.query)

(def default-xtql-query "(from :docs [xt/id foo])")
(def default-sql-query "SELECT *, _valid_from FROM docs")

(defn default [tx-type]
  (if (= tx-type :xtql)
    default-xtql-query
    default-sql-query))
