(ns xt-play.view
  (:require ["@heroicons/react/24/outline" :refer [BookmarkIcon CheckCircleIcon]]
            ["@heroicons/react/24/solid"
             :refer [ArrowUturnLeftIcon PencilIcon PlayIcon XMarkIcon]]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [xt-play.components.dropdown :refer [dropdown]]
            [xt-play.components.editor :as editor]
            [xt-play.components.highlight :as hl]
            [xt-play.model.client :as model]
            [xt-play.model.run :as run]
            [xt-play.model.tx-batch :as tx-batch]))

(defn language-dropdown []
  (let [tx-type @(rf/subscribe [:get-type])]
    [dropdown {:items model/items
               :selected tx-type
               :on-click #(rf/dispatch [:dropdown-selection (:value %)])
               :label (get (model/value->label model/items) tx-type)}]))

(defn spinner [] [:div "Loading..."])

(defn display-error [{:keys [exception message data]}]
  [:div {:class "flex flex-col gap-2"}
   [:div {:class "bg-red-100 border-l-4 border-red-500 text-red-700 p-4"}
    [:p {:class "font-bold"} (str "Error: " exception)]
    [:p {:class "whitespace-pre-wrap font-mono"}
     (->> (str/split message #"(?:\r\n|\r|\n)")
          (map #(do [:span %]))
          (interpose [:br]))]
    (when (seq data)
      [:<>
       [:p {:class "pt-2 font-semibold"}
        "Data:"]
       [:p (pr-str data)]])]])

(defn display-table [results type]
  (when results
    [:table {:class "table-auto w-full"}
     [:thead
      [:tr {:class "border-b"}
       (for [label (first results)]
         ^{:key label}
         [:th {:class "text-left p-4"} label])]]
     [:tbody
      (for [[i row] (map-indexed vector (rest results))]
        ^{:key (str "row-" i)}
        [:tr {:class "border-b"}
         (for [[ii value] (map-indexed vector row)]
           ^{:key (str "row-" i " col-" ii)}
           [:td {:class "text-left p-4"}
            (case type
              :xtql
              [hl/code {:language "clojure"}
               (pr-str value)]
              ;; default
              [hl/code {:language "json"}
               (js/JSON.stringify (clj->js value))])])])]]))

(defn title [& body]
  (into [:h2 {:class "text-lg font-semibold"}]
        body))

(defn button [opts & body]
  (into [:button (merge {:class "bg-blue-500 hover:bg-blue-700 text-white font-bold py-1 px-3 rounded-sm"}
                        opts)]
        body))

(defn run-button []
  [button {:class "bg-blue-500 hover:bg-blue-700 text-white font-bold py-1 px-3 rounded-sm"
           :on-click #(rf/dispatch [::run/run])}
   [:div {:class "flex flex-row gap-1 items-center"}
    "Run"
    [:> PlayIcon {:class "h-5 w-5"}]]])

(defn copy-button []
  (let [copy-tick @(rf/subscribe [:copy-tick])]
    [:div {:class (str "p-2 flex flex-row gap-1 items-center select-none"
                       (when-not copy-tick
                         " hover:bg-gray-300 cursor-pointer"))
           :disabled copy-tick
           :on-click #(rf/dispatch-sync [:copy-url])}
     (if-not copy-tick
       [:<>
        "Copy URL"
        [:> BookmarkIcon {:class "h-5 w-5"}]]
       [:<>
        "Copied!"
        [:> CheckCircleIcon {:class "h-5 w-5"}]])]))

(def logo
  [:a {:href "/"}
   [:div {:class "flex flex-row items-center gap-1"}
    [:img {:class "h-8"
           :src "/public/images/xtdb-full-logo.svg"}]
    [title "Play"]]])

(defn header []
  [:header {:class "sticky top-0 z-50 bg-gray-200 py-2 px-4"}
   [:div {:class "container mx-auto flex flex-col md:flex-row items-center gap-1"}
    [:div {:class "w-full flex flex-row items-center gap-4"}
     logo
     [:span {:class "text-sm text-gray-400"}
      @(rf/subscribe [:version])]]
    [:div {:class "max-md:hidden flex-grow"}]
    [:div {:class "w-full flex flex-row items-center gap-1 md:justify-end"}
     [:div {:class "md:hidden flex-grow"}]
     [language-dropdown]
     [copy-button]
     [run-button]]]])

(def beta-copy
  (str "We are currently testing a new SQL framework for XTDB Play which utilises more of XTDB 2.0s powerful new features. "
       "Feel free to stick arround and have a play, but if you want to return to safty, select a different mode from the dropdown"))

(defn- beta-banner []
  (let [expanded? (r/atom false)]
    (fn []
      [:footer {:class "sticky bottom-0 z-50 bg-red-200 py-2 px-4"}
       [:div {:class "container text-red-900 mx-auto flex flex-col items-center gap-1 cursor-pointer"
              :on-click #(swap! expanded? not)}
        (if-not @expanded?
          ;; todo, this can be nicer.
          [:p {:class "fa-regular fa-question-circle"}
           " You are in beta mode. Click here to find out more."]
          [:p beta-copy])]])))

(defn reset-system-time-button [id]
  [:> ArrowUturnLeftIcon
   {:class "h-5 w-5 cursor-pointer"
    :on-click #(rf/dispatch
                [:fx [[:dispatch [::tx-batch/assoc id :system-time nil]]
                      [:dispatch [:update-url]]]])}])

(defn input-system-time [id system-time]
  ;; TODO: Show the picker when someone clicks the edit button
  ;;       https://developer.mozilla.org/en-US/docs/Web/API/HTMLInputElement/showPicker
  [:input {:type "date"
           :value (-> system-time .toISOString (str/split #"T") first)
           :on-change #(rf/dispatch [:fx [[:dispatch [::tx-batch/assoc id :system-time (js/Date. (.. % -target -value))]]
                                          [:dispatch [:update-url]]]])
           :max (-> (js/Date.) .toISOString (str/split #"T") first)}])

(defn edit-system-time-button [id]
  [:> PencilIcon {:className "h-5 w-5 cursor-pointer"
                  :on-click #(rf/dispatch [:fx [[:dispatch [::tx-batch/assoc id :system-time (js/Date. (.toDateString (js/Date.)))]]
                                                [:dispatch [:update-url]]]])}])

(defn single-transaction [{:keys [editor id]} {:keys [system-time txs]}]
  [:div {:class "h-full flex flex-col"}
   (when system-time
     [:div {:class "flex flex-row justify-center items-center py-1 px-5 bg-gray-200"}
      [input-system-time id system-time]
      [reset-system-time-button id]])
   [editor {:class "border md:flex-grow min-h-36"
            :source txs
            :on-change #(rf/dispatch [:fx [[:dispatch [::tx-batch/assoc id :txs %]]
                                           [:dispatch [:update-url]]]])}]])

(defn multiple-transactions [{:keys [editor]} tx-batches]
  [:<>
   (for [[id {:keys [system-time txs]}] tx-batches]
     ^{:key id}
     [:div {:class "flex flex-col"}
      [:div {:class "flex flex-row justify-between items-center py-1 px-5 bg-gray-200"}
       [:div {:class "w-full flex flex-row gap-2 justify-center items-center"}
        (if (nil? system-time)
          [:<>
           [:div "Current Time"]
           [edit-system-time-button id]]
          [:<>
           [input-system-time id system-time]
           [reset-system-time-button id]])]
       [:> XMarkIcon {:class "h-5 w-5 cursor-pointer"
                      :on-click #(rf/dispatch [:fx [[:dispatch [::tx-batch/delete id]]
                                                    [:dispatch [:update-url]]]])}]]
      [editor {:class "border md:flex-grow min-h-36"
               :source txs
               :on-change #(rf/dispatch [:fx [[:dispatch [::tx-batch/assoc id :txs %]]
                                              [:dispatch [:update-url]]]])}]])])

(defn transactions [{:keys [editor]}]
  [:div {:class "mx-4 md:mx-0 md:ml-4 md:flex-1 flex flex-col"}
   [:h2 "Transactions:"]
   ; NOTE: The min-h-0 somehow makes sure the editor doesn't
   ;       overflow the flex container
   [:div {:class "grow min-h-0 overflow-y-auto flex flex-col gap-2"}
    (let [tx-batches @(rf/subscribe [::tx-batch/id-batch-pairs])]
      (if (= 1 (count tx-batches))
        (let [[id batch] (first tx-batches)]
          [single-transaction {:editor editor
                               :id id}
           batch])
        [multiple-transactions {:editor editor}
         tx-batches]))
    [:div {:class "flex flex-row justify-center"}
     [:button {:class "w-10 h-10 bg-blue-500 hover:bg-blue-700 text-white font-bold py-1 rounded-full"
               :on-click #(rf/dispatch [:fx [[:dispatch [::tx-batch/append tx-batch/blank]]
                                             [:dispatch [:update-url]]]])}
      "+"]]]])

(defn query [{:keys [editor]}]
  [:div {:class "mx-4 md:mx-0 md:mr-4 md:flex-1 flex flex-col"}
   [:h2 "Query:"]
   [editor {:class "md:flex-grow h-full min-h-36 border"
            :source @(rf/subscribe [:query])
            :on-change #(rf/dispatch [:fx [[:dispatch [:set-query %]]
                                           [:dispatch [:update-url]]]])}]])

(def ^:private initial-message [:p {:class "text-gray-400"} "Enter a query to see results"])
(def ^:private no-results-message "No results returned")
(defn- empty-rows-message [results] (str (count results) " empty row(s) returned"))

(defn results []
  [:section {:class "md:h-1/2 mx-4 flex flex-1 flex-col"}
   [:h2 "Results:"]
   [:div {:class "grow min-h-0 border p-2 overflow-auto"}
    (if @(rf/subscribe [::run/loading?])
      [spinner]
      (let [{::run/keys [results failure response?]} @(rf/subscribe [::run/results-or-failure])]
        (if failure
          [display-error failure]
          (cond
            (not response?) initial-message
            (empty? results) no-results-message
            (every? empty? results) (empty-rows-message results)
            :else
            [display-table results @(rf/subscribe [:get-type])]))))]])

(defn app []
  (let [tx-type (rf/subscribe [:get-type])]
    (fn []
      [:div {:class "flex flex-col h-dvh"}
       [header]
       ;; overflow-hidden fixes a bug where if an editor would have content that goes off the
       ;; screen the whole page would scroll.
       [:div {:class "py-2 flex-grow md:overflow-hidden h-full flex flex-col gap-2"}
        [:section {:class "md:h-1/2 flex flex-col md:flex-row flex-1 gap-2"}
         (let [editor (case @tx-type
                        :xtql
                        editor/clj-editor
                        ;; default
                        editor/sql-editor)]
           [:<>
            [transactions {:editor editor}]
            [:hr {:class "md:hidden"}]
            [query {:editor editor}]
            [:div {:class "md:hidden flex flex-col items-center"}
             [run-button]]])]
        (when (or @(rf/subscribe [::run/loading?])
                  @(rf/subscribe [::run/results?]))
          [:<>
           [:hr {:class "md:hidden"}]
           [results]])]
       (when (= :sql-beta @tx-type)
         [beta-banner])])))
