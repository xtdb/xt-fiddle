(ns xt-play.util
  #?(:clj (:require [clojure.edn :as edn])))

#?(:clj
   (def xt-version
     (-> (slurp "deps.edn")
         (edn/read-string)
         (get-in [:deps 'com.xtdb/xtdb-core :mvn/version]))))

#?(:clj
   (def read-edn
     (partial edn/read-string {:readers *data-readers*})))

(defn map-results->rows
  [results]
  [(if (seq (:error results))
     results
     {:result
      (if (every? map? results)
        (let [ks (keys (apply merge results))]
          (into [(vec ks)]
                (mapv (fn [row]
                        (mapv #(get row %) ks))
                      results)))
        results)})])


(defn is-query? [s]
  (re-find #"(?i)^\s*(WITH\s+.*?SELECT|SELECT)\b" s))
