(ns xt-play.view
  (:require ["@heroicons/react/24/outline"
             :refer [BookmarkIcon CheckCircleIcon]]
            ["@heroicons/react/24/solid"
             :refer [PlayIcon XMarkIcon CheckIcon]]
            ["react-svg-spinners" :refer [SixDotsScale]]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [xt-play.components.dropdown :refer [dropdown]]
            [xt-play.components.editor :as editor]
            [xt-play.components.highlight :as hl]
            [xt-play.config :as config]
            [xt-play.model.client :as model]
            [xt-play.model.run :as run]
            [xt-play.model.tx-batch :as tx-batch]))

;; Todo
;; - pull out components to own ns

(defn- language-dropdown [tx-type]
  [dropdown {:items model/items
             :selected tx-type
             :on-click #(rf/dispatch [:dropdown-selection (:value %)])
             :label (get-in config/tx-types [tx-type :label])}])

(defn- spinner [] [:div [:> SixDotsScale]])

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

(defn- display-table [results tx-type position]
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
                 (str value)])]))]))]]))

(defn- title [& body]
  (into [:h2 {:class "text-lg font-semibold"}]
        body))

(defn- update-db-from-editor-refs [tx-refs]
  (doseq [tx-ref @tx-refs]
    (rf/dispatch [::tx-batch/assoc (:id @tx-ref) :txs (get-value tx-ref)])))

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
      [:div {:class "flex flex-row gap-1 items-center"}
       "Run"
       [:> PlayIcon {:class "h-5 w-5"}]]]
     [:> CheckIcon
      {:class "h-5 w-5"
       :visibility (if (and (not @loading?)
                            @show-results?)
                     "visible"
                     "hidden")}]]))

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

(defn- header [tx-type tx-refs]
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
     [copy-button tx-refs]
     [run-button tx-refs]]]])

(def ^:private initial-message [:p {:class "text-gray-400"} "Enter a statement to see results"])
(def ^:private no-results-message "No results returned")
(defn- empty-rows-message [results] (str (count results) " empty row(s) returned"))

(defn- display-tx-result [idx row]
  (let [[[msg-k msg] & more] row]
    (case msg-k
      "message" (let [[[_exc exc] [_data data]] more]
                    [display-error {:message msg :exception exc :data data} idx])
      "next.jdbc/update-count" ^{:key idx} [:p.mb-2.mx-2 "Transaction succeeded."]
      :else nil)))

(defn- tx-result? [row]
  (let [[[[msg-k _msg] _]] row]
    (#{"next.jdbc/update-count" "message"} msg-k)))

(defn- spacer-header [cnt children]
  [:div.px-1 {:class (when (> cnt 1) "pt-8")}
   children])

(def half-window-col-class (str "md:mx-0 flex-1 flex flex-col "
                                ;; stop editor expanding beyond the viewport
                                "md:max-w-[48vw] lg:max-w-[49vw] "))

(defn- tx-results
  "If there is a system-time - there was a transaction, so discard those results."
  [{:keys [system-time]} the-result]
  (if system-time
    (map-indexed display-tx-result (drop 1 (drop-last the-result)))
    (map-indexed display-tx-result the-result)))

(defn- results [position]
  (let [tx-batches @(rf/subscribe [::tx-batch/id-batch-pairs])
        statements (second (get tx-batches position))
        tx-type (rf/subscribe [:get-type])
        loading? (rf/subscribe [::run/loading?])
        show-results? (rf/subscribe [::run/show-results?])
        results-or-failure (rf/subscribe [::run/results-or-failure])]
    (fn []
      (when (or @loading?
                @show-results?)
        ^{:key position}
        [:div {:class (str half-window-col-class
                           "grow min-h-0 border overflow-auto")}
         (if @loading?
           [spinner]
           (let [{::run/keys [results failure response?]} @results-or-failure]
             (if failure
               [display-error failure position]
               (let [the-result (get results position)]
                 (if (tx-result? the-result)
                   [spacer-header (count results)
                    (tx-results statements the-result)]
                   (cond
                     (not response?) [spacer-header (count results)
                                      initial-message]
                     (empty? results) [spacer-header (count results)
                                       no-results-message]
                     (every? empty? the-result) [spacer-header (count results)
                                                 (empty-rows-message the-result)]
                     :else
                     [display-table the-result tx-type position]))))))]))))

(defn- captions-row [text]
  (let [show-results? (rf/subscribe [::run/show-results?])]
    [:div {:class "flex flex-row"}
     [:div {:class (str "mx-4 md:mr-4 " half-window-col-class)}
      [:h2 text]]
     [:div {:class (str "mx-4 md:mx-0 " half-window-col-class)}
      [:div {:class "flex ml-4 flex-row items-center justify-between"}
       [:h2 "Results:"]
       [:> XMarkIcon {:class "h-5 w-5 cursor-pointer"
                      :visibility (if @show-results? "visible" "hidden")
                      :on-click #(rf/dispatch [::run/hide-results!])}]]]]))

(defn- rm-stmt-header [id system-time]
  [:div {:class "flex flex-row justify-between items-center py-1 px-5 bg-gray-200 "}
   [:div {:class "w-full flex flex-row gap-2 justify-center items-center"}
    (when system-time (str system-time))]
   [:> XMarkIcon {:class "h-5 w-5 cursor-pointer"
                  :on-click #(rf/dispatch [:fx [[:dispatch [::tx-batch/delete id]]
                                                [:dispatch [:update-url]]]])}]])

(defn- add-statement-button []
  [:div {:class "flex flex-row"}
   [:div {:class "flex-col flex-1"}
    [:div {:class "flex flex-row justify-center"}
     [:button {:class "w-10 h-10 bg-blue-500 hover:bg-blue-700 text-white font-bold py-1 rounded-full"
               :on-click #(rf/dispatch [:fx [[:dispatch [::tx-batch/append tx-batch/blank]]
                                             [:dispatch [:update-url]]
                                             [:dispatch [::run/hide-results!]]]])}
      "+"]]]
   [:div {:class "flex-col flex-1"
          :visibility "hidden"}]])

(defn- statements [{:keys [editor tx-refs]} tx-type]
  (reset! tx-refs [])
  (let [tx-batches @(rf/subscribe [::tx-batch/id-batch-pairs])]
    [:<>
     [captions-row "Statements:"]
     [:<>
      (doall
       (map-indexed
        (fn [idx [id {:keys [txs system-time]}]]
          (let [ref (atom {:id id :ref nil})]
            (swap! tx-refs conj ref)
            ^{:key id}
            [:<>
             [:div {:class "flex flex-row"}
              [:div {:class (str half-window-col-class
                                 "mx-4 md:pr-4 grow min-h-0 overflow-y-auto ")}
               (when (< 1 (count tx-batches))
                 [rm-stmt-header id system-time])
               [editor (merge
                        (editor-update-opts id txs ref)
                        {:class "md:flex-grow min-h-36 border"})]]

              [results idx]]]))
        tx-batches))]
     (when (#{:sql-v2 :xtql} tx-type)
       [add-statement-button])]))

(def ^:private mobile-gap [:hr {:class "md:hidden"}])

(defn app []
  (let [tx-type (rf/subscribe [:get-type])
        tx-refs (atom [])]
    (fn []
      [:div {:class "flex flex-col h-dvh"}
       [header @tx-type tx-refs]
       ;; overflow-hidden fixes a bug where if an editor would have content that
       ;; goes off the screen the whole page would scroll.
       [:div {:class "py-2 px-4 flex-grow  h-full flex flex-row md:flex-row gap-2 "}
        (let [ctx {:editor (editor/default-editor @tx-type)
                   :tx-refs tx-refs}]
          [:div {:class "flex flex-col gap-2 w-full"}
           [statements ctx @tx-type]
           mobile-gap])]])))
