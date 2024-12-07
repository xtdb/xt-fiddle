(ns xt-play.test-runner
  (:require
   [cljs.test :refer-macros [run-tests]]
   [xt-play.model.run-test]))

(defn format-results [results]
  (str "<pre>" (with-out-str (cljs.test/do-report results)) "</pre>"))

(defn append-results-to-page [results]
  (let [div (.createElement js/document "div")]
    (set! (.-innerHTML div) (format-results results))
    (.appendChild js/document.body div)))

(defn run-all-tests []
  (let [results (run-tests 'xt-play.model.run-test)]
    (append-results-to-page results)))

(set! *main-cli-fn* run-all-tests)
