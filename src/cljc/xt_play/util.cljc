(ns xt-play.util
  (:require #?(:clj [clojure.edn :as edn])))

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
  (let [ks (keys (apply merge results))]
    (into [(vec ks)]
          (mapv (fn [row]
                  (mapv #(get row %) ks))
                results))))
