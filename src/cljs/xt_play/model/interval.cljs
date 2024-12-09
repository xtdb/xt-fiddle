(ns xt-play.model.interval
  (:require [re-frame.core :as rf]))

(defonce interval-handler
  (let [live-intervals (atom {})]
    (fn handler [{:keys [action id freq event]}]
      (case action
        :clean   (doall (map #(handler {:action :end  :id  %1}) (keys @live-intervals)))
        :start   (swap! live-intervals assoc id (js/setInterval #(rf/dispatch event) freq))
        :end     (do (js/clearInterval (get @live-intervals id)) 
                     (swap! live-intervals dissoc id))))))

;; when this code is reloaded `:clean` existing intervals
(interval-handler {:action :clean})

(rf/reg-fx ::interval interval-handler)

(rf/reg-event-fx
 ::start-editing
 (fn [_]
   {::interval {:action :start
               :id :editing
               :freq 1000
               :event [:update-url]}}))

(rf/reg-event-fx
 ::stop-editing
 (fn [_]
   {::interval {:action :end
               :id :editing}}))
