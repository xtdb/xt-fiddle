(ns xt-play.query)

;; >> API

(def default-xtql-query "(from :docs [xt/id foo])")
(def default-sql-query "SELECT *, _valid_from FROM docs")

(defn default [type]
  (case type
    :xtql
    default-xtql-query
    ;;default
    default-sql-query))
