;; mutation-tested: 2026-05-06
(ns isaac.tool.fs-bounds
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.fs :as fs]
    [isaac.session.store :as store]
    [isaac.session.store.file :as file-store])
  (:import
    [java.io File]))

(defn canonical-path [path]
  (.getCanonicalPath (io/file path)))

(defn path-inside? [parent child]
  (let [parent (canonical-path parent)
        child  (canonical-path child)]
    (or (= parent child)
        (str/starts-with? child (str parent File/separator)))))

(defn state-dir->home [state-dir]
  (if (= ".isaac" (.getName (io/file state-dir)))
    (.getParent (io/file state-dir))
    state-dir))

(defn config-directories [state-dir]
  (set [(str state-dir "/config")
        (str state-dir "/.isaac/config")]))

(defn crew-quarters [state-dir crew-id]
  (str state-dir "/crew/" crew-id))

(defn string-key-map [m]
  (into {} (map (fn [[k v]] [(if (keyword? k) (name k) (str k)) v]) m)))

(defn arg-bool [args k default]
  (let [value (get args k)]
    (cond
      (nil? value)     default
      (boolean? value) value
      (string? value)  (= "true" (str/lower-case value))
      :else            (boolean value))))

(defn arg-int [args k default]
  (let [value (get args k)]
    (cond
      (nil? value)     default
      (integer? value) value
      (string? value)  (parse-long value)
      :else            default)))

(defn allowed-directories [args]
  (let [args        (string-key-map args)
        session-key (get args "session_key")
        state-dir   (get args "state_dir")]
    (when (and session-key state-dir)
      (when-let [session (store/get-session (file-store/create-store state-dir) session-key)]
        (let [crew-id     (or (:crew session) "main")
              quarters    (crew-quarters state-dir crew-id)
              _           (fs/mkdirs quarters)
              cfg         (config/load-config {:home (state-dir->home state-dir)})
              directories (or (get-in cfg [:crew crew-id :tools :directories]) [])]
          (vec (concat [quarters]
                       (keep (fn [directory]
                               (cond
                                 (= :cwd directory) (:cwd session)
                                 (= "cwd" directory) (:cwd session)
                                 (string? directory) directory
                                 :else nil))
                             directories))))))))

(defn path-outside-error [file-path]
  {:isError true :error (str "path outside allowed directories: " file-path)})

(defn ensure-path-allowed [args file-path]
  (let [state-dir (get (string-key-map args) "state_dir")]
    (when-let [directories (seq (allowed-directories args))]
      (let [denied-config? (some #(path-inside? % file-path) (config-directories state-dir))]
        (when (or denied-config?
                  (not-any? #(path-inside? % file-path) directories))
          (path-outside-error file-path))))))
