(ns user
  (:require [xt-play.main :as main]
            [clojure.java.browse :as browse]
            [clojure.java.io :as io]
            [clojure.tools.namespace.repl :as repl]
            [integrant.core :as ig]
            [integrant.repl :as igr :refer [go halt]]
            [lambdaisland.classpath.watch-deps :as watch-deps]))

(defn browse [port]
  (browse/browse-url (str "http://localhost:" port)))

(defn browse! []
  (browse 8000)
  (browse 9630))

(defn watch-deps!
  []
  (watch-deps/start! {:aliases [:dev :test]}))

(igr/set-prep! #(ig/prep main/system))

(def go! go)

(defn go!! []
  (go!)
  (watch-deps!)
  (browse!))

(comment
  (repl/set-refresh-dirs (io/file "src") (io/file "dev"))
  (repl/refresh)
  (repl/clear)

  (watch-deps!)

  (go)
  (halt)
  (browse 8000))
