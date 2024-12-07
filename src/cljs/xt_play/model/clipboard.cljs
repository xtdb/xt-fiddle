(ns xt-play.model.clipboard
  (:require [re-frame.core :as rf]))

(rf/reg-fx ::set
  (fn [{:keys [text on-success on-failure]}]
    (-> (js/navigator.clipboard.writeText text)
        (.then #(when on-success
                  (rf/dispatch on-success)))
        (.catch #(when on-failure
                   (rf/dispatch (conj on-failure %)))))))
