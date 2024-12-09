(ns xt-play.handler-test
  (:require [clojure.edn :as edn]
            [clojure.test :as t]
            [next.jdbc :as jdbc]
            [xt-play.handler :as h]
            [xtdb.api :as xt]))

;; todo:
;; [ ] test unhappy paths
;; [ ] test wider range of scenarios / formats
;; [ ] test to pipeline
;; [ ] assert format from client

(defn- t-file [path]
  (edn/read-string (slurp (format "test-resources/%s.edn" path))))

(t/deftest run-handler-test
  (t/testing "xtql example returns expected results"
    (t/is (= {:status 200, :body [[:foo :xt/id] ["bar" 1]]}
             (h/run-handler (t-file "xtql-example-request")))))

  (t/testing "sql example returns expected results"
    (t/is (= {:status 200,
              :body
              [["_id" "col1" "col2"]
               [2 "bar" " baz"]
               [1 "foo" nil]]}
             (h/run-handler (t-file "sql-example-request")))))

  (t/testing "beta sql example returns expected results"
    (t/is (= {:status 200,
              :body
              [[:_id :col1 :col2]
               [2 "bar" " baz"]
               [1 "foo" nil]]}
             (h/run-handler (t-file "beta-sql-example-request"))))))

(t/deftest run-handler-multi-transactions-test
  (t/testing "multiple transactions in xtql"
    (t/is (= {:status 200, :body [[:foo :xt/id]
                                  ["baz" 2]
                                  ["bar" 1]]}
             (h/run-handler
              (assoc-in
               (t-file "xtql-example-request")
               [:parameters :body :tx-batches]
               [{:txs "[:put-docs :docs {:xt/id 1 :foo \"bar\"}]",
                 :system-time "2024-12-01T00:00:00.000Z"}
                {:txs "[:put-docs :docs {:xt/id 2 :foo \"baz\"}]",
                 :system-time nil}])))))

  (t/testing "multiple transacions on sql"
    (t/is (= {:status 200,
              :body
              [["_id" "col1" "col2" "_valid_from"]
               [2 "bar" " baz" #time/zoned-date-time "2024-12-02T00:00Z[UTC]"]
               [1 "foo" nil #time/zoned-date-time "2024-12-01T00:00Z[UTC]"]]}
             (h/run-handler
              (-> (t-file "sql-example-request")
                  (assoc-in
                   [:parameters :body :tx-batches]
                   [{:txs "INSERT INTO docs (_id, col1) VALUES (1, 'foo');",
                     :system-time "2024-12-01T00:00:00.000Z"}
                    {:txs "INSERT INTO docs RECORDS {_id: 2, col1: 'bar', col2:' baz'};",
                     :system-time "2024-12-02T00:00:00.000Z"}])
                  (assoc-in
                   [:parameters :body :query]
                   "SELECT *, _valid_from FROM docs"))))))

  (t/testing "beta sql can run multiple txs"
    (t/is (= {:status 200,
              :body
              [[:_id :col1 :col2 :_valid_from]
               [2 "bar" " baz" #inst "2024-12-02T00:00:00.000000000-00:00"]
               [1 "foo" nil #inst "2024-12-01T00:00:00.000000000-00:00"]]}
             (h/run-handler
              (-> (t-file "beta-sql-example-request")
                  (assoc-in
                   [:parameters :body :tx-batches]
                   [{:txs "INSERT INTO docs (_id, col1) VALUES (1, 'foo');",
                     :system-time "2024-12-01T00:00:00.000Z"}
                    {:txs "INSERT INTO docs RECORDS {_id: 2, col1: 'bar', col2:' baz'};",
                     :system-time "2024-12-02T00:00:00.000Z"}])
                  (assoc-in
                   [:parameters :body :query]
                   "SELECT *, _valid_from FROM docs")))))))

(t/deftest beta-sql-run-features
  (t/testing "Column order is maintained"
    (t/is (= {:status 200,
              :body [[:_id :a :b :c :d :e :f :g :h :j]
                     [1 2 3 4 5 6 7 8 9 10]]}
             (h/run-handler
              (assoc-in
               (t-file "beta-sql-example-request")
               [:parameters :body :tx-batches]
               [{:txs "INSERT INTO docs RECORDS {_id: 1, a: 2, b: 3, c: 4, d: 5, e: 6, f: 7, g: 8, h: 9, j: 10}"
                 :system-time nil}])))))

  (t/testing "execute payload is not mutated"
    (let [txs (atom [])]
      (with-redefs [jdbc/execute! (fn [_conn statement & args]
                                    (swap! txs conj statement))]
        (h/run-handler (t-file "beta-sql-example-request"))
        (t/is
         (= [[(str "INSERT INTO docs (_id, col1) VALUES (1, 'foo');\n"
                   "INSERT INTO docs RECORDS {_id: 2, col1: 'bar', col2:' baz'};")]
             ["SELECT * FROM docs"]]
            @txs)))))

  (t/testing "xt submit-tx sql payload is reformatted"
    (let [txs (atom [])]
      (def txs txs)
      (with-redefs [xt/submit-tx (fn [_node tx & args]
                                   (swap! txs conj tx))]
        (h/run-handler (t-file "sql-example-request"))
        (t/is
         (= [[[:sql "INSERT INTO docs (_id, col1) VALUES (1, 'foo')"]
              [:sql "\nINSERT INTO docs RECORDS {_id: 2, col1: 'bar', col2:' baz'}"]]]
            @txs))))))

(t/deftest sql-says-carol-is-red-test
  (t/testing "XTDB docs example for sql https://docs.xtdb.com/quickstart/sql-overview.html"
    (t/is (= {:status 200,
              :body
              [["name" "favorite_color" "_valid_from" "_system_from"]
               ["carol"
                "red"
                #time/zoned-date-time "2023-09-01T00:00Z[UTC]"
                #time/zoned-date-time "2024-01-08T00:00Z[UTC]"]]}
             (h/run-handler (t-file "sql-multi-transaction")))))

  (t/testing "Bob still likes fishing - don't determine columns based on the first row"
    (t/is (= {:status 200,
              :body
              [["_id" "favorite_color" "name" "likes"]
               [2 "red" "carol" nil]
               [9 nil "bob" ["fishing" 3.14 {"nested" "data"}]]]}
             (h/run-handler
              (assoc-in
               (t-file "sql-multi-transaction")
               [:parameters :body :query]
               "SELECT * FROM people"))))))

(t/deftest beta-sql-says-carol-is-red-test
  (t/testing "XTDB docs example for sql https://docs.xtdb.com/quickstart/sql-overview.html"
    (t/is (= {:status 200,
              :body
              [[:name :favorite_color :_valid_from :_system_from]
               ["carol"
                "red"
                #inst "2023-08-31T23:00:00.000000000-00:00"
                #inst "2024-01-08T00:00:00.000000000-00:00"]]}
             (h/run-handler (assoc-in
                             (t-file "sql-multi-transaction")
                             [:parameters :body :tx-type]
                             "sql-beta")))))

  (t/testing "Bob still likes fishing - don't determine columns based on the first row"
    (t/is (= {:status 200,
             :body
             [[:_id :favorite_color :info :likes :name]
              [2 "red" nil nil "carol"]
              [9 nil nil ["fishing" 3.14 {:nested "data"}] "bob"]]}
             (h/run-handler
              (->  (t-file "sql-multi-transaction")
                   (assoc-in [:parameters :body :query] "SELECT * FROM people")
                   (assoc-in [:parameters :body :tx-type] "sql-beta")))))))

(t/deftest docs-run
  (t/testing "docs run returns map results"
    (t/is
     (= {:status 200,
         :body
         [{"_id" 2, "favorite_color" "red", "name" "carol"}
          {"_id" 9, "likes" ["fishing" 3.14 {"nested" "data"}], "name" "bob"}]}
        (h/docs-run-handler
         (->  (t-file "sql-multi-transaction")
              (assoc-in [:parameters :body :query] "SELECT * FROM people")))))))
