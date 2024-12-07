(ns xt-play.model.query-params
  (:require [re-frame.core :as rf]))

(defn get-query-params []
  (->> (js/URLSearchParams. (.-search js/window.location))
       (map (fn [param]
              (let [[k v] (js->clj param)]
                {(keyword k) v})))
       (into {})))

(rf/reg-cofx
 ::get
 (fn [cofx _]
   (assoc cofx :query-params (get-query-params))))

(defn ->query-string [params]
  (let [search-params (js/URLSearchParams.)]
    (doseq [[k v] params]
      (.set search-params (name k) (str v)))
    (.toString search-params)))

(defn set-query-params [params]
  (let [url (js/URL. js/window.location.href)]
    (set! (.-search url) (->query-string params))
    ;; We replace the history instead of pushing a new state
    ;; because it's a pain to hook up the back and forward buttons
    (js/window.history.replaceState "" "" (.toString url))))

(rf/reg-fx ::set set-query-params)
