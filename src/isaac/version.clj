(ns isaac.version
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]))

(defn- deps-manifest-version []
  (try
    (or (some-> "deps.edn" slurp edn/read-string :isaac/manifest :version)
        (some-> (clojure.java.io/resource "deps.edn") slurp edn/read-string :isaac/manifest :version))
    (catch Exception _ nil)))

(defn read-git-sha []
  (try
    (let [head (str/trim (slurp ".git/HEAD"))]
      (if (str/starts-with? head "ref: ")
        (let [ref-path (str ".git/" (str/trim (subs head 5)))
              sha      (str/trim (slurp ref-path))]
          (when (>= (count sha) 7) (subs sha 0 7)))
        (when (>= (count head) 7) (subs head 0 7))))
    (catch Exception _ nil)))

(defn version-string []
  (let [v   (or (deps-manifest-version) "unknown")
        sha (read-git-sha)]
    (if sha
      (str "isaac " v " (" sha ")")
      (str "isaac " v))))
