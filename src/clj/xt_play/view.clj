(ns xt-play.view
  (:require [hiccup.page :as h]
            [xt-play.util :as util]))

(def index
  (h/html5
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1"}]
    [:meta {:name "description"
            :content ""}]
    [:link {:rel "stylesheet"
            :href "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/default.min.css"}]
    [:link {:rel "stylesheet"
            :type "text/css"
            :href "/public/css/main.css"}]
    [:script {:src "https://cdn.tailwindcss.com"}]
    [:script {:async true
              :defer true
              :data-website-id "aabeabcb-ad76-47a4-9b4b-bef3fdc39af4"
              :src "https://bunseki.juxt.pro/umami.js"}]
    [:title "XTDB Play"]]
   [:body
    [:div {:id "app"}]
    [:script {:type "text/javascript"
              :src "/public/js/compiled/app.js"}]
    [:script {:type "text/javascript"}
     (str "var xt_version = '" util/xt-version "';")]
    [:script {:type "text/javascript"}
     "xt_play.app.init()"]]))
