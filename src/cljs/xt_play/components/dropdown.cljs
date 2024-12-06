(ns xt-play.components.dropdown
  (:require [reagent.core :as r]))

(defn dropdown [{:keys [label selected items on-click]}]
  (r/with-let [id (str (gensym "dropdown"))
               open? (r/atom false)
              ; Close the dropdown if the user clicks outside of it
               click-handler (fn [event]
                               (when @open?
                                 (let [dropdown-elem (js/document.querySelector (str "#" id))]
                                   (when (not (.contains dropdown-elem (.-target event)))
                                     (reset! open? false)))))
               _ (js/window.addEventListener "click" click-handler)]
    [:div {:class "relative inline-block text-left"}
     [:button {:type "button"
               :class "inline-flex justify-center w-full px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-sm shadow-sm hover:bg-gray-50 focus:outline-none"
               :id id
               :data-dropdown-toggle "dropdown"
               :on-change #(reset! open? false)
               :on-click #(swap! open? not)}
      label
      [:svg {:class "w-4 h-4 ml-2"
             :xmlns "http://www.w3.org/2000/svg"
             :fill "none"
             :viewBox "0 0 24 24"
             :stroke "currentColor"
             :aria-hidden "true"}
       [:path
        {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M19 9l-7 7-7-7"}]]]

     (when @open?
       [:div {:class "z-10 absolute w-44 bg-white divide-y divide-gray-100 shadow dark:bg-gray-700"}
        [:ul {:class "py-1 text-sm text-gray-700 dark:text-gray-200" :aria-labelledby "dropdownDefault"}
         (doall
          (for [{:keys [value label] :as item} items]
            ^{:key value}
            [:li
             [:a {:href "#"
                  :class (str "block px-4 py-2 "
                              (if (= selected value)
                                "bg-gray-100 text-gray-400 cursor-default"
                                "hover:bg-gray-200 dark:hover:bg-gray-600 dark:hover:text-white"))
                  :on-click (fn [event]
                              (.preventDefault event)
                              (when-not (= selected value)
                                (on-click item))
                              (reset! open? false))}
              label]]))]])]
    (finally
      (js/window.removeEventListener "click" click-handler))))
