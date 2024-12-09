(ns xt-play.model.run-test
  (:require
   [cljs.test :as t]
   [xt-play.model.run :as model]
   [xt-play.model.tx-batch :as batch]))

(def app-db
  {:version "2.0.0-beta3",
   :type :sql,
   :query "SELECT *, _valid_from FROM docs",
   ::batch/list [::batch/tx5],
   ::batch/id->batch
   {::batch/tx5
    {:system-time #inst "2024-12-05T00:00:00.000-00:00",
     :txs
     "INSERT INTO docs (_id, col1) VALUES (1, 'foo');\nINSERT INTO docs RECORDS {_id: 2, col1: 'bar', col2:' baz'};"}},
   ::model/response? true,
   ::model/results
   [["_id" "col1" "col2" "_valid_from"]
    [2 "bar" " baz" "2024-12-06T16:29:11.264541Z"]
    [1 "foo" nil "2024-12-06T16:29:11.264541Z"]]})

(t/deftest run-test
  (let [{app-db-after :db
         opts :http-xhrio} (model/run app-db)]

    (t/testing "app-db is in expected state"
      (t/is (= (-> app-db
                   (assoc ::model/loading? true)
                   (dissoc ::model/response? ::model/results))
               app-db-after)))

    (t/testing "request is as expected"
      (t/is (= {:method :post,
                :uri "/beta-db-run",
                :params
                {:tx-type :sql,
                 :query "SELECT *, _valid_from FROM docs",
                 :tx-batches
                 [{:system-time "2024-12-05T00:00:00.000Z",
                   :txs
                   "INSERT INTO docs (_id, col1) VALUES (1, 'foo');\nINSERT INTO docs RECORDS {_id: 2, col1: 'bar', col2:' baz'};"}]},
                :timeout 3000,
                :on-success [::model/request-success],
                :on-failure [::model/request-failure]}
               (dissoc opts :format :response-format))))))

(t/run-tests)
