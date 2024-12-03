(ns xt-play.util)

(defn map-results->rows
  [results]
  (let [ks (keys (first results))]
    (into [(vec ks)]
          (mapv (fn [row]
                  (mapv #(get row %) ks))
                results))))

