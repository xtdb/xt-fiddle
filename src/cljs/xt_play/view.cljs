(ns xt-play.view
  (:require ["@heroicons/react/24/outline"
             :refer [BookmarkIcon CheckCircleIcon]]
            ["@heroicons/react/24/solid"
             :refer [PlayIcon XMarkIcon BugAntIcon]]
            ["react-svg-spinners" :refer [SixDotsScale]]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [xt-play.components.dropdown :refer [dropdown]]
            [xt-play.components.editor :as editor]
            [xt-play.components.highlight :as hl]
            [xt-play.config :as config]
            [xt-play.model.client :as model]
            [xt-play.model.run :as run]
            [xt-play.model.tx-batch :as tx-batch]
            [xt-play.util :as util]))

;; Todo
;; - pull out components to own ns

(defn- language-dropdown [tx-type]
  [dropdown {:items model/items
             :selected tx-type
             :on-click #(do
                          (rf/dispatch [:dropdown-selection (:value %)])
                          (rf/dispatch [::run/hide-results!]))
             :label (get-in config/tx-types [tx-type :label])}])

(defn- spinner [] [:div {:class "flex justify-center items-center h-full"}
                   [:> SixDotsScale]])

(defn get-value [ref]
  (when-let [refd @ref]
    (let [doc (.. (:ref refd) -view -viewState -state -doc)
          text-lines (if-let [children (.-children doc)]
                       ;; get each chile's text
                       (.map children (fn [c] (.. c -text (join "\n"))))
                       ;; no children - just get text
                       (.-text doc))]
      (.join text-lines "\n"))))

(defn- editor-update-opts [id source ref]
  {:source source
   :editor-ref ref
   :on-blur #(do
               (rf/dispatch [::tx-batch/assoc id :txs (get-value ref)])
               (rf/dispatch [:update-url]))})

(defn- display-error [{:keys [exception message data]} position]
  ^{:key position}
  [:div {:class "flex flex-col gap-2"}
   [:div {:class "bg-red-100 border-l-4 border-red-500 text-red-700 p-4"}
    [:p {:class "font-bold"} (str "Error: " exception)]
    [:p {:class "whitespace-pre-wrap font-mono"}
     (->> (str/split message #"(?:\r\n|\r|\n)")
          (map-indexed #(do ^{:key (str "span-" %1)} [:<> [:span %2] [:br]])))]
    (when (seq data)
      [:<>
       [:p {:class "pt-2 font-semibold"}
        "Data:"]
       [:p (pr-str data)]])]])

(defn- display-warnings [warnings]
  [:div {:class "flex flex-col gap-2"}
   [:div {:class "bg-amber-500 border-l-4 border-amber-600 text-gray-500 p-4"}
    [:p {:class "font-bold"} "Warnings:"]
    [:p {:class "whitespace-pre-wrap font-mono"}
     (->> warnings
          (map-indexed #(do ^{:key (str "span-" %1)} [:<> [:span %2] [:br]])))]]])

(defn- display-table [results position]
  (let [tx-type (rf/subscribe [:get-type])]
    (when results
      ^{:key position}
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
                   (if (or (vector? value)
                           (map? value))
                     (js/JSON.stringify (clj->js value) nil 1)
                     (str value))])]))]))]])))

(defn- title [& body]
  (into [:h2 {:class "text-lg font-semibold"}]
        body))

(defn- update-db-from-editor-refs [tx-refs]
  (doseq [tx-ref @tx-refs]
    (let [value (get-value tx-ref)]
      (if (empty? value)
        (rf/dispatch [::tx-batch/delete (:id @tx-ref)])
        (rf/dispatch [::tx-batch/assoc (:id @tx-ref) :txs (get-value tx-ref)])))))

(def icon-size "h-5 w-5")

(def icon-pointer (str icon-size " cursor-pointer"))

(defn- run-button [tx-refs]
  (let [loading? (rf/subscribe [::run/loading?])
        show-results? (rf/subscribe [::run/show-results?])]
    [:<>
     [:button {:class "bg-blue-500 hover:bg-blue-700 text-white font-bold py-1 px-3 rounded-sm"
               :disabled @loading?
               :opacity (if @loading? 0.4 1)
               :on-click #(do
                            (update-db-from-editor-refs tx-refs)
                            (rf/dispatch [::run/run]))}
      [:div {:class "flex flex-row gap-1 items-center"
             :title "Press 'CMD+ENTER' or 'CTRL+ENTER' to run"}
       "Run"
       [:> PlayIcon {:class icon-size}]]]
     [:> XMarkIcon {:class icon-pointer
                    :visibility (if (and (not @loading?) @show-results?) "visible" "hidden")
                    :on-click #(rf/dispatch [::run/hide-results!])}]]))

(defn- copy-button [tx-refs]
  (let [copy-tick (rf/subscribe [:copy-tick])]
    (fn []
      [:div {:class (str "p-2 flex flex-row gap-1 items-center select-none"
                         (when-not @copy-tick
                           " hover:bg-gray-300 cursor-pointer"))
             :disabled @copy-tick
             :on-click (fn [_]
                         ;; given that it's no longer controlled component - need to get values
                         ;; of the editor from the refs
                         (update-db-from-editor-refs tx-refs)

                         (rf/dispatch-sync [:update-url])
                         (rf/dispatch-sync [:copy-url]))}

       (if-not @copy-tick
         [:div {:class "flex flex-row items-center gap-1"
                :title "Share your state by Copying the URL"}
          "Copy URL"
          [:> BookmarkIcon {:class icon-size}]]
         [:div {:class "flex flex-row items-center gap-4"}
          "Copied!"
          [:> CheckCircleIcon {:class icon-size}]])])))

(def ^:private logo
  [:a {:href "/"}
   [:div {:class "flex flex-row items-center gap-1"}
    [:img {:class "h-8"
           :src "/public/images/xtdb-full-logo.svg"}]
    [title "Play"]]])

(defn- header [tx-type tx-refs]
  [:header {:class "max-md:sticky top-0 z-50 bg-gray-200 py-2 px-4"}
   [:div {:class "container mx-auto flex flex-col sm:flex-row items-center gap-1"}
    [:div {:class "w-full flex flex-row items-center gap-4"}
     logo
     [:span {:class "text-sm text-gray-400"}
      @(rf/subscribe [:version])]
     [:div {:class "flex flex-row"}
      [:a {:class "text-sm text-gray-400"
           :href "https://docs.xtdb.com/quickstart/sql-overview"}
       "SQL Overview"]]
     [:div {:class "flex flex-row text-sm text-gray-400"}
      [:a {:class icon-pointer
           :href "https://github.com/xtdb/xt-fiddle/issues/new"}
       [:> BugAntIcon {:title "See a bug? Report it here!"}]]]]
    [:div {:class "max-md:hidden flex-grow"}]
    [:div {:class "w-full flex flex-row items-center gap-1 md:justify-end"}
     [language-dropdown tx-type]
     [:div {:class "md:hidden flex-grow"}]
     [copy-button tx-refs]
     [run-button tx-refs]]]])

(def ^:private initial-message [:p {:class "text-gray-400"} "Enter a statement to see results"])
(def ^:private no-results-message [:div {:class "pl-2 pt-2"}
                                   "No results returned"])
(defn- empty-rows-message [results] [:div {:class "pl-2 pt-2"}
                                     (str (count results) " empty row(s) returned")])

(defn- tx-result-or-error? [row]
  (or (= [[]] (:result row))
      (contains? row :error)))

(defn- spacer-header [cnt children]
  [:div
   (when (> cnt 1)
     [:div {:class "flex flex-row justify-between items-center py-1 px-5 bg-gray-200 "}
      [:> XMarkIcon {:class icon-size
                     :visibility "hidden"}]])
   children])

(def half-window-col-class (str "sm:mx-0 flex-1 flex flex-col "
                                ;; stop editor expanding beyond the viewport
                                "md:max-w-[48vw] lg:max-w-[49vw] "))

(defn- prune-tx-results [results]

  (if (and (> (count results) 2)
           (every? tx-result-or-error?
                   results))
    (drop 1 (drop-last results))
    (:pruned
     (reduce (fn [{:keys [tx-err] :as acc} res]
               (if (tx-result-or-error? [res])
                 (cond
                   (= 1 tx-err) (-> acc
                                    (update :tx-err inc)
                                    (update :pruned conj res))
                   (= 2 tx-err) (assoc acc :tx-err 0)
                   :else (update acc :tx-err inc))
                 (update acc :pruned conj res)))
             {:tx-err 0
              :pruned []}
             results))))

(defn- results [position]
  (let [tx-batches @(rf/subscribe [::tx-batch/id-batch-pairs])
        statements (second (get tx-batches position))
        loading? (rf/subscribe [::run/loading?])
        show-results? (rf/subscribe [::run/show-results?])
        results-or-failure (rf/subscribe [::run/results-or-failure])]
    (when (or @loading?
              @show-results?)
      ^{:key position}
      [:div {:class (str half-window-col-class
                         "grow h-auto max-h-none border overflow-visible ")}
       (if @loading?
         [spinner]
         (let [{::run/keys [results failure response?]} @results-or-failure]
           (if failure
             [display-error failure position]
             (let [prune-tx-results-fn (if (:system-time statements)
                                         prune-tx-results
                                         identity)
                   the-result (prune-tx-results-fn (get results position))]
               [spacer-header (count results)
                (map-indexed
                 (fn [idx {:keys [result error warnings]}]
                   ^{:key idx}
                   [:<>
                    (cond
                      (not response?) initial-message
                      (seq error) [display-error error (str position "-" idx)]
                      (and (util/is-query? (:txs statements))
                           (= [[]] result)) no-results-message
                      (= [[]] result) [:div {:class "pl-2 pt-2"}
                                       "Statement succeeded."]
                      (every? empty? result) (empty-rows-message result)
                      (seq result) [display-table result (str position "-" idx)]
                      :else no-results-message)
                    (when (seq warnings)
                      [display-warnings warnings])])
                 the-result)]))))])))

(defn- rm-stmt-header [id system-time position]
  [:div {:class "flex flex-row justify-between items-center py-1 px-5 bg-gray-200 "}
   [:div {:class "w-full flex flex-row gap-2 justify-center items-center"}
    (when system-time (str system-time))]
   [:> XMarkIcon {:class icon-pointer
                  :on-click #(rf/dispatch [:fx [[:dispatch [::tx-batch/delete id]]
                                                [:dispatch [::run/delete-result position]]
                                                [:dispatch [:update-url]]]])}]])

(defn- add-statement-button []
  [:div {:class "flex flex-col sm:flex-row"}
   [:div {:class "flex-col flex-1"}
    [:div {:class "flex flex-row justify-center"}
     [:button {:class "w-10 h-10 bg-blue-500 hover:bg-blue-700 text-white font-bold py-1 rounded-full"
               :on-click #(rf/dispatch [:fx [[:dispatch [::tx-batch/append tx-batch/blank]]
                                             [:dispatch [:update-url]]
                                             [:dispatch [::run/hide-results!]]]])}
      "+"]]]
   [:div {:class "flex-col flex-1"
            :visibility "hidden"}]])

(defn- statements [{:keys [editor tx-refs]}]
  (reset! tx-refs [])
  (let [statements @(rf/subscribe [::tx-batch/id-batch-pairs])]
    [:<>
     [:<>
      (doall
       (map-indexed
        (fn [idx [id {:keys [txs system-time]}]]
          (let [ref (atom {:id id :ref nil})]
            (swap! tx-refs conj ref)
            ^{:key id}
            [:<>
             [:div {:class "flex flex-col sm:flex-row gap-y-2"}
              [:div {:class (str half-window-col-class
                                 " grow h-fit overflow-visible sm:overflow-y-auto sm:pr-4 ")}
               (when (< 1 (count statements))
                 [rm-stmt-header id system-time idx])
               [editor (merge
                        (editor-update-opts id txs ref)
                        {:class "md:flex-grow h-fit sm:min-h-32 border"})]]

              [results idx]]]))
        statements))]
     [add-statement-button]]))

(def ^:private mobile-gap [:hr {:class "sm:hidden"}])

(defn app []
  (let [tx-type (rf/subscribe [:get-type])
        tx-refs (atom [])]
    (fn []
      [:div {:class "flex flex-col h-dvh w-screen"}
       [header @tx-type tx-refs]
       [:div {:class "py-2 sm:px-4 flex-grow h-full flex flex-row gap-2 "}
        (let [ctx {:editor (editor/default-editor @tx-type)
                   :tx-refs tx-refs}]
          [:div {:class "flex flex-col gap-4 sm:gap-2 w-full"}
           [statements ctx]
           mobile-gap])]])))
