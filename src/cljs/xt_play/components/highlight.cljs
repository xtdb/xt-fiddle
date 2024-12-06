(ns xt-play.components.highlight
  (:require ["highlight.js/lib/core" :as hljs]
            ["highlight.js/lib/languages/clojure" :as hljs-clojure]
            ["highlight.js/lib/languages/json" :as hljs-json]))

(defn setup []
  (hljs/registerLanguage "clojure" hljs-clojure)
  (hljs/registerLanguage "json" hljs-json))

(defn render-raw-html [html-content]
  [:div {:dangerouslySetInnerHTML {:__html html-content}}])

(defn code [{:keys [language]} code]
  [render-raw-html (.-value (hljs/highlight code #js {:language language}))])
