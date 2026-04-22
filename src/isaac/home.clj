(ns isaac.home
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [isaac.fs :as fs]
    [isaac.logger :as log]))

(def ^:dynamic *resolved-home* nil)
(def ^:dynamic *user-home* nil)

(defn user-home []
  (or *user-home* (System/getProperty "user.home")))

(defn current-home []
  (or *resolved-home* (user-home)))

(defn- absolute-path [path]
  (if (and (string? path) (str/starts-with? path "/"))
    path
    (str (System/getProperty "user.dir") "/" path)))

(defn- expand-tilde [path]
  (cond
    (not (string? path)) path
    (= "~" path)        (user-home)
    (str/starts-with? path "~/") (str (user-home) (subs path 1))
    :else path))

(defn- pointer-value [path]
  (when (fs/exists? path)
    (try
      (let [data (edn/read-string (fs/slurp path))
            home (:home data)]
        (when (string? home)
          (expand-tilde home)))
      (catch Exception _
        (log/warn :home/pointer-file-invalid :path path)
        nil))))

(defn- pointer-home []
  (or (pointer-value (str (user-home) "/.config/isaac.edn"))
      (pointer-value (str (user-home) "/.isaac.edn"))))

(defn resolve-home [explicit-home fallback-home]
  (-> (or explicit-home
          fallback-home
          (pointer-home)
          (user-home))
      absolute-path))

(defn extract-home-flag [args]
  (loop [remaining args
         stripped  []
         explicit  nil]
    (if-let [arg (first remaining)]
      (cond
        (= "--home" arg)
        (if-let [value (second remaining)]
          (recur (nnext remaining) stripped value)
          {:args args :home explicit})

        (str/starts-with? arg "--home=")
        (recur (rest remaining) stripped (subs arg (count "--home=")))

        :else
        (recur (rest remaining) (conj stripped arg) explicit))
      {:args stripped :home explicit})))
