(ns xt-play.view
  (:require ["@heroicons/react/24/outline"
             :refer [BookmarkIcon CheckCircleIcon QuestionMarkCircleIcon]]
            ["@heroicons/react/24/solid"
             :refer [ArrowUturnLeftIcon PencilIcon PlayIcon XMarkIcon]]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [xt-play.components.dropdown :refer [dropdown]]
            [xt-play.components.editor :as editor]
            [xt-play.components.highlight :as hl]
            [xt-play.config :as config]
            [xt-play.model.client :as model]
            [xt-play.model.interval :as i]
            [xt-play.model.run :as run]
            [xt-play.model.tx-batch :as tx-batch]))

;; Todo
;; - pull out components to own ns

(defn- language-dropdown [tx-type]
  [dropdown {:items model/items
             :selected tx-type
             :on-click #(rf/dispatch [:dropdown-selection (:value %)])
             :label (get-in config/tx-types [tx-type :label])}])

(defn- spinner [] [:div "Loading..."]) ;; todo spinners spin

(defn- editor-update-opts [id source]
  {:source source
   :on-focus #(rf/dispatch [::i/start-editing])
   :on-change #(rf/dispatch (if (= :query id)
                              [:set-query %]
                              [::tx-batch/assoc id :txs %]))
   :on-blur #(do
               (rf/dispatch [:update-url])
               (rf/dispatch [::i/stop-editing]))})

(defn- display-error [{:keys [exception message data]}]
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

(defn- display-table [results tx-type]
  (when results
    [:table {:class "table-auto w-full"}
     [:thead
      [:tr {:class "border-b"}
       (for [label (first results)]
         ^{:key label}
         [:th {:class "text-left p-4"} label])]]
     [:tbody
      (doall
       (for [[i row] (map-indexed vector (rest results))]
         ^{:key (str "row-" i)}
         [:tr {:class "border-b"}
          (doall
           (for [[ii value] (map-indexed vector row)]
             ^{:key (str "row-" i " col-" ii)}
             [:td {:class "text-left p-4"}
              (case @tx-type
                :xtql
                [hl/code {:language "clojure"}
                 (pr-str value)]
                ;; default
                [hl/code {:language "json"}
                 (js/JSON.stringify (clj->js value))])]))]))]]))

(defn- title [& body]
  (into [:h2 {:class "text-lg font-semibold"}]
        body))

(defn- button [opts & body]
  (into [:button (merge {:class "bg-blue-500 hover:bg-blue-700 text-white font-bold py-1 px-3 rounded-sm"}
                        opts)]
        body))

(defn- run-button []
  [button {:class "bg-blue-500 hover:bg-blue-700 text-white font-bold py-1 px-3 rounded-sm"
           :on-click #(rf/dispatch [::run/run])}
   [:div {:class "flex flex-row gap-1 items-center"}
    "Run"
    [:> PlayIcon {:class "h-5 w-5"}]]])

(defn- copy-button []
  (let [copy-tick (rf/subscribe [:copy-tick])]
    (fn []
      [:div {:class (str "p-2 flex flex-row gap-1 items-center select-none"
                         (when-not @copy-tick
                           " hover:bg-gray-300 cursor-pointer"))
             :disabled @copy-tick
             :on-click (fn [_]
                         ;; for more fluid typing, we only update url on
                         ;; blur. This means that sometimes the url hasn't got
                         ;; the latest updates from the app-db. Ensure that's
                         ;; not the case by updating before copying
                         (rf/dispatch-sync [:update-url])
                         (rf/dispatch-sync [:copy-url]))}

       (if-not @copy-tick
         [:<>
          "Copy URL"
          [:> BookmarkIcon {:class "h-5 w-5"}]]
         [:<>
          "Copied!"
          [:> CheckCircleIcon {:class "h-5 w-5"}]])])))

(def ^:private logo
  [:a {:href "/"}
   [:div {:class "flex flex-row items-center gap-1"}
    [:img {:class "h-8"
           :src "/public/images/xtdb-full-logo.svg"}]
    [title "Play"]]])

(defn- header [tx-type]
  [:header {:class "max-md:sticky top-0 z-50 bg-gray-200 py-2 px-4"}
   [:div {:class "container mx-auto flex flex-col md:flex-row items-center gap-1"}
    [:div {:class "w-full flex flex-row items-center gap-4"}
     logo
     [:span {:class "text-sm text-gray-400"}
      @(rf/subscribe [:version])]]
    [:div {:class "max-md:hidden flex-grow"}]
    [:div {:class "w-full flex flex-row items-center gap-1 md:justify-end"}
     [language-dropdown tx-type]
     [:div {:class "md:hidden flex-grow"}]
     [copy-button]
     [run-button]]]])

(def beta-copy
  (str "We are currently testing a new SQL framework for XTDB Play which utilises more of XTDB 2.0s powerful new features. "
       "Feel free to stick arround and have a play, but if you want to return to safty, select a different mode from the dropdown"))

(defn- beta-banner []
  (when (:show-beta? config/config)
    (let [expanded? (r/atom false)]
      (fn []
        [:footer {:class "sticky max-md:hidden bottom-0 z-50 bg-red-200 py-2 px-4"}
         [:div {:class "container text-red-900 mx-auto flex flex-col items-center gap-1 cursor-pointer"
                :on-click #(swap! expanded? not)}
          (if-not @expanded?
            [:div {:class "flex items-center gap-1"}
             "You are in beta mode."
             [:> QuestionMarkCircleIcon {:class "h-5 w-5"}]]
            [:p beta-copy])]]))))

(defn- reset-system-time-button [id]
  [:> ArrowUturnLeftIcon
   {:class "h-5 w-5 cursor-pointer"
    :on-click #(rf/dispatch
                [:fx [[:dispatch [::tx-batch/assoc id :system-time nil]]
                      [:dispatch [:update-url]]]])}])

(defn- input-system-time [id system-time]
  ;; TODO: Show the picker when someone clicks the edit button
  ;;       https://developer.mozilla.org/en-US/docs/Web/API/HTMLInputElement/showPicker
  [:input {:type "date"
           :value (-> system-time .toISOString (str/split #"T") first)
           :on-change #(rf/dispatch [:fx [[:dispatch [::tx-batch/assoc id :system-time (js/Date. (.. % -target -value))]]
                                          [:dispatch [:update-url]]]])
           :max (-> (js/Date.) .toISOString (str/split #"T") first)}])

(defn- edit-system-time-button [id]
  [:> PencilIcon {:className "h-5 w-5 cursor-pointer"
                  :on-click #(rf/dispatch [:fx [[:dispatch [::tx-batch/assoc id :system-time (js/Date. (.toDateString (js/Date.)))]]
                                                [:dispatch [:update-url]]]])}])

(defn- single-transaction [{:keys [editor id]} {:keys [system-time txs]}]
  [:div {:class "h-full flex flex-col"}
   (when system-time
     [:div {:class "flex flex-row justify-center items-center py-1 px-5 bg-gray-200"}
      [input-system-time id system-time]
      [reset-system-time-button id]])
   [editor (merge
            (editor-update-opts id txs)
            {:class "border md:flex-grow min-h-36"})]])

(defn- multiple-transactions [{:keys [editor]} tx-batches]
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
      [editor (merge
               (editor-update-opts id txs)
               {:class "border md:flex-grow min-h-36"})]])])

(defn- transactions [{:keys [editor]}]
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

(defn- query [{:keys [editor]}]
  [:div {:class "mx-4 md:mx-0 md:mr-4 md:flex-1 flex flex-col"}
   [:h2 "Query:"]
   [editor (merge
            (editor-update-opts :query @(rf/subscribe [:query]))
            {:class "md:flex-grow h-full min-h-36 border"})]])

(def ^:private initial-message [:p {:class "text-gray-400"} "Enter a query to see results"])
(def ^:private no-results-message "No results returned")
(defn- empty-rows-message [results] (str (count results) " empty row(s) returned"))

(defn- results []
  (let [tx-type (rf/subscribe [:get-type])
        loading? (rf/subscribe [::run/loading?])
        results-or-failure (rf/subscribe [::run/results-or-failure])]
    (fn []
      [:section {:class "md:h-1/2 mx-4 flex flex-1 flex-col"}
       [:h2 "Results:"]
       [:div {:class "grow min-h-0 border p-2 overflow-auto"}
        (if @loading?
          [spinner]
          (let [{::run/keys [results failure response?]} @results-or-failure]
            (if failure
              [display-error failure]
              (cond
                (not response?) initial-message
                (empty? results) no-results-message
                (every? empty? results) (empty-rows-message results)
                :else
                [display-table results tx-type]))))]])))

(def ^:private mobile-gap [:hr {:class "md:hidden"}])

(defn app []
  (let [tx-type (rf/subscribe [:get-type])
        loading? (rf/subscribe [::run/loading?])
        results? (rf/subscribe [::run/results?])]
    (fn []
      [:div {:class "flex flex-col h-dvh"}
       [header @tx-type]
       ;; overflow-hidden fixes a bug where if an editor would have content that
       ;; goes off the screen the whole page would scroll.
       [:div {:class "py-2 flex-grow md:overflow-hidden h-full flex flex-col gap-2"}
        [:section {:class "md:h-1/2 flex flex-col md:flex-row flex-1 gap-2"}
         (let [ctx {:editor (editor/default-editor @tx-type)}]
           [:<>
            [transactions ctx]
            mobile-gap
            [query ctx]
            mobile-gap])]
        (when (or @loading? @results?)
          [results])]
       (when (= :sql-beta @tx-type)
         [beta-banner])])))
