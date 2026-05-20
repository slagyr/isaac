(ns isaac.util.shell
  (:require [clojure.java.shell :as sh]))

(def ^:dynamic *sh* nil)
(def ^:dynamic *os-name* nil)

(defn sh! [& args]
  (if *sh* (apply *sh* args) (apply sh/sh args)))

(defn os-name []
  (or *os-name* (System/getProperty "os.name")))

(defn cmd-available? [cmd]
  (= 0 (:exit (sh/sh "which" cmd))))
