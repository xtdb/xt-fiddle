(ns xt-play.views.help
  (:require ["@heroicons/react/24/outline" :refer [QuestionMarkCircleIcon]]
            [re-frame.core :as rf]
            [xt-play.components.popup :as popup]))

(defn- mono [t] [:span.font-mono.bg-gray-100.text-sm.p-1.rounded t])
(defn- link [t [href]] [:a.hover:underline.text-orange-700.font-medium.cursor-pointer {:href href
                                                                                       :target "_blank"} t ])

(defn- subheading [& t]
  [:h2 {:class "pt-2 text-xl font-semibold text-gray-900"}
   t
   [:hr]])

(defn- paragraph [& t]
  [:p {:class "py-2"} t])

(def help-body
  [:div
   [:p {:class "text-lg text-gray-900 my-1"}
    "Welcome to xt-fiddle, a playground for "
    [link "xtdb" ["https://docs.xtdb.com/index.html"]] "."]
   [subheading "Usage"]

   [paragraph
    "Below the navbar you'll see two panels: " [mono "transactions"] " and " [mono "query"] "."]

   [paragraph
    "In the " [mono "transactions"] " panel you can write colon (" [mono ";"] ") separated "
    [link "transactions" ["https://docs.xtdb.com/reference/main/sql/txs"]]
    " that will all be executed at the same "
    [link "system time" ["https://docs.xtdb.com/quickstart/sql-overview.html#system-time-columns-automatic-time-versioning-of-rows-without-audit-tables"]]
    ". To execute transactions at a different or multiple system times then click the " [mono "+"] " button and adjust the date above each panel. "
    "Make sure that each system time is greater or equal to the previous one, just like when you execute transactions in real time!"]

   [paragraph
    "In the " [mono "query"] " panel you can write a single " [link "query" ["https://docs.xtdb.com/reference/main/sql/queries.html"]] [:span.italic " after "] "all the transactions have been run. "]

   [paragraph
    "You can use the " [mono "run"] " button to execute the transactions then run the query."]

   [paragraph
    "Share your current state by clicking the " [mono "copy url button"] " then share the generated url."]

   [subheading "Docs"]

   [paragraph
    "For more information on xtdb and a lot more tutorials, here are the " [link "docs" ["https://docs.xtdb.com/index.html"]] "."]
   
   [paragraph
    "Something not working as you expect with xtdb-play? Open an issue "
    ;; todo - could template a more meaningul issue, including the session link
    [link "here" ["https://github.com/xtdb/xt-fiddle/issues/new?template=Blank+issue"]] "."]

   [paragraph
    "Found a bug in xtdb? Open an issue "
    ;; todo - could template a more meaningul issue, including the session link
    [link "here" ["https://github.com/xtdb/xtdb/issues/new?template=Blank+issue"]] "."]

   ;; todo - get the exact wording before spending time on formatting!
   ])

(defn slider []
  [:<>
   [:div {:class "cursor-pointer"
          :on-click #(rf/dispatch [::popup/toggle :help])}
    [:> QuestionMarkCircleIcon {:class ["h-5 w-5"
                                        "hover:text-gray-500"]}]]
   [popup/view {:title "help" :body help-body}]])
