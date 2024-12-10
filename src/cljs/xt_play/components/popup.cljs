(ns xt-play.components.popup
  (:require [re-frame.core :as rf]
            [clojure.string :as str]))

(rf/reg-event-db
 ::open
 (fn [db [_ id]]
   ;; only one popup open at a time
   (assoc db ::open id)))

(rf/reg-sub
 ::open
 :-> ::open)

(rf/reg-event-fx
 ::toggle
 (fn [{:keys [db]} [_ id]]
   {:dispatch (if (= id (::open db))
                [::close]
                [::open id])}))

(rf/reg-event-db
 ::close
 (fn [db]
   (dissoc db ::open)))

(defn- close [_] (rf/dispatch [::close]))

(def close-btn
  [:div {:class ["absolute" "right-5" "top-0" "-ml-8" "flex"
                 "pr-2" "pt-4" "sm:-ml-10" "sm:pr-4"]}
   [:button {:type "button"
             :on-click close
             :class ["relative" "rounded-md" "text-gray-400" "hover:text-gray-500"
                     "hover:ring-gray-500" "hover:ring-2"
                     "focus:outline-none" "focus:ring-2" "focus:ring-white"]}
    [:span {:class "absolute -inset-2.5"}]
    [:span {:class "sr-only"} "Close panel"]
    [:svg {:class "size-6"
           :fill "none"
           :viewBox "0 0 24 24"
           :stroke-width "1.5"
           :stroke "currentColor"
           :aria-hidden "true"
           :data-slot "icon"}
     [:path {:stroke-linecap "round"
             :stroke-linejoin "round"
             :d "M6 18 18 6M6 6l12 12"}]]]])

(def backdrop
  [:div {:class "fixed inset-0 bg-gray-500/75 transition-opacity"
         :aria-hidden "true"
         :on-click close}])

(defn heading [title]
  [:h1 {:class "text-2xl font-semibold text-gray-900 "
        :id "slide-over-title"}
   (str/capitalize title)])

(defn content [body]
  [:div {:class "relative mt-6 flex-1"}
         body])

(defn slide-over-panel [{:keys [title body backdrop?]}]
  [:div {:class "relative  z-[100] bg-blue"
         :aria-labelledby "slide-over-title"
         :role "dialog"
         :aria-modal "true"}
   (when backdrop? backdrop)
   [:div {:class "absolute overflow-hidden"}
    [:div {:class "absolute inset-0 overflow-hidden"}
     [:div {:class "fixed inset-y-0 right-0 flex max-w-full pl-10"}
      [:div {:class "relative w-screen max-w-md"}
       [:div {:class ["px-4 sm:px-6" "flex h-full flex-col overflow-y-scroll bg-white py-6 shadow-xl"]}
        close-btn
        [heading title]
        [content body]]]]]]])

(defn view [content]
  (when @(rf/subscribe [::open])
    [slide-over-panel content]))

;;(rf/dispatch [::open :help])

;;(rf/dispatch [::close])

