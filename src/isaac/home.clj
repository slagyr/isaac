(ns isaac.home
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [isaac.fs :as fs]
    [isaac.logger :as log]))

(def ^:dynamic *resolved-home* nil)
(def ^:dynamic *user-home* nil)
(def ^:dynamic *state-dir* nil)

(defonce ^:private root-state-dir* (atom nil))

(defn user-home []
  (or *user-home* (System/getProperty "user.home")))

(defn current-home []
  (or *resolved-home* (user-home)))

(defn state-dir
  "Returns the current state directory. Thread-local binding takes priority,
   then the process-wide root set by init-state-dir!, then derives from current-home."
  []
  (or *state-dir* @root-state-dir* (str (current-home) "/.isaac")))

(defn init-state-dir!
  "Sets the process-wide default state directory. Called at server boot so
   all threads can reach state-dir without explicit threading."
  [dir]
  (reset! root-state-dir* dir))

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

(defn- pointer-value [path fs*]
  (when (fs/exists? fs* path)
    (try
      (let [data (edn/read-string (fs/slurp fs* path))
            home (:home data)]
        (when (string? home)
          (expand-tilde home)))
      (catch Exception _
        (log/warn :home/pointer-file-invalid :path path)
        nil))))


(defn- pointer-home [fs*]
  (or (pointer-value (str (user-home) "/.config/isaac.edn") fs*)
      (pointer-value (str (user-home) "/.isaac.edn") fs*)))

(defn resolve-home
  [explicit-home fallback-home fs*]
  (-> (or explicit-home
          fallback-home
          (pointer-home fs*)
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
