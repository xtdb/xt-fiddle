(ns xt-play.util
  (:require #?(:clj [clojure.edn :as edn])
            #?(:clj [clojure.instant :refer [read-instant-date]])))

#?(:clj
   (def xt-version
     (-> (slurp "deps.edn")
         (edn/read-string)
         (get-in [:deps 'com.xtdb/xtdb-core :mvn/version]))))

#?(:clj
   (def read-edn
     (partial edn/read-string {:readers *data-readers*})))

(defn format-system-time [s]
  ;; todo, understand what we use and why - do we need to convert?
  (when s
    #?(:clj (read-instant-date s) ;; sent to xt
       :cljs (.toISOString s)     ;; sent to server
       )))

(defn map-results->rows
  [results]
  (let [ks (keys (first results))]
    (into [(vec ks)]
          (mapv (fn [row]
                  (mapv #(get row %) ks))
                results))))
