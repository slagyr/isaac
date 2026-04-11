(ns isaac.util.shell
  (:require [clojure.java.shell :as sh]))

(defn cmd-available? [cmd]
  (= 0 (:exit (sh/sh "which" cmd))))
