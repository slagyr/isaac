(ns isaac.session.context
  (:require
    [isaac.fs :as fs]))

(defn read-boot-files [cwd]
  (when cwd
    (let [path (str cwd "/AGENTS.md")]
      (when (fs/exists? path)
        (fs/slurp path)))))
