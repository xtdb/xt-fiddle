(ns xt-play.components.editor
  (:require ["@codemirror/autocomplete" :refer [autocompletion]]
            ["@codemirror/commands" :refer [defaultKeymap history historyKeymap indentWithTab]]
            ["@codemirror/language" :refer [foldGutter syntaxHighlighting defaultHighlightStyle]]
            ["@codemirror/lang-sql" :refer [sql PostgreSQL]]
            ["@codemirror/state" :refer [EditorState]]
            ["@codemirror/view" :as view :refer [EditorView lineNumbers]]
            ["@uiw/react-codemirror$default" :as CodeMirror]
            [applied-science.js-interop :as j]
            [nextjournal.clojure-mode :as cm-clj]))

(def theme
  (.theme EditorView
          (j/lit {; General styling
                  ".cm-content" {:white-space "pre-wrap"
                                 :padding "10px 0"
                                 :flex "1 1 0"}
                  ".cm-line" {:padding "0 9px"
                              :line-height "1.6"
                              :font-size "16px"
                              :font-family "var(--code-font)"}
                  ".cm-matchingBracket" {:border-bottom "1px solid var(--teal-color)"
                                         :color "inherit"}
                  ".cm-gutters" {:background "transparent"
                                 :padding "0 9px"
                                 :line-height "1.6"
                                 :font-size "16px"
                                 :font-family "var(--code-font)"}
                  ; Ensure editor is full height
                  "&.cm-editor" {:height "100%"}
                  ; No border when focused
                  "&.cm-focused" {:outline "0 !important"}
                  ;; Only show cursor when focused
                  ".cm-cursor" {:visibility "hidden"}
                  "&.cm-focused .cm-cursor" {:visibility "visible"}})))

(defn js-concat [& colls]
  (apply array (apply concat colls)))

(defonce clj-extensions
  #js [theme
       (history)
       (view/drawSelection)
       (syntaxHighlighting defaultHighlightStyle)
       (foldGutter)
       (lineNumbers)
       (.. EditorState -allowMultipleSelections (of true))
       cm-clj/default-extensions
       (.of view/keymap (js-concat cm-clj/complete-keymap historyKeymap))])

(def sql-extensions
  #js [theme
       (history)
       (view/drawSelection)
       (syntaxHighlighting defaultHighlightStyle #js{:fallback true})
       (foldGutter)
       (lineNumbers)
       (.. EditorState -allowMultipleSelections (of true))
       (.of view/keymap (js-concat defaultKeymap historyKeymap [indentWithTab]))
       ;; IMO too annoying to use
       #_(autocompletion)
       (sql #js{:dialect PostgreSQL
                #_#_:schema #js{"xt$txs" #js["xt$id" "xt$committed?"
                                             "xt$error" "xt$tx_time"
                                             "xt$valid_from" "xt$valid_to"
                                             "xt$system_from" "xt$system_to"]}
                :upperCaseKeywords true})])

(defn editor [{:keys [source extensions on-change] my-class :class}]
  [:div {:class my-class}
    [:> CodeMirror {:value source
                    :extensions extensions
                    :basicSetup false
                    :className "h-full"
                    :on-change on-change}]])

(defn clj-editor [{:keys [source on-change] my-class :class}]
  [editor {:source source
           :extensions clj-extensions
           :on-change on-change
           :class my-class}])

(defn sql-editor [{:keys [source on-change] my-class :class}]
  [editor {:source source
           :extensions sql-extensions
           :on-change on-change
           :class my-class}])
