;; mutation-tested: 2026-05-06
(ns isaac.tool.fs-bounds
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.fs :as fs]
    [isaac.session.store :as store]
    [isaac.session.store.file :as file-store]
    [isaac.system :as system])
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

(defn session-workdir
  "Return the session's cwd as a string if it exists as a directory, else nil."
  [session-key]
  (when session-key
    (when-let [state-dir (system/get :state-dir)]
      (when-let [cwd (:cwd (store/get-session (or (system/get :session-store) (file-store/create-store state-dir)) session-key))]
        (when (.isDirectory (io/file cwd))
          cwd)))))

(defn resolve-path
  "Resolve a path against session-cwd:
   nil/blank/'.' → session-cwd, relative → joined with session-cwd, absolute → as-is.
   Returns nil when both path is nil/blank and session-cwd is nil."
  [path session-cwd]
  (cond
    (or (nil? path) (str/blank? path) (= "." path)) session-cwd
    (.isAbsolute (io/file path))                      path
    session-cwd                                       (.getCanonicalPath (io/file session-cwd path))
    :else                                             path))

(defn allowed-directories [args]
  (let [args        (string-key-map args)
        session-key (get args "session_key")
        state-dir   (system/get :state-dir)]
    (when (and session-key state-dir)
      (when-let [session (store/get-session (or (system/get :session-store) (file-store/create-store state-dir)) session-key)]
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
  (when-let [directories (seq (allowed-directories args))]
    (let [state-dir      (system/get :state-dir)
          denied-config? (some #(path-inside? % file-path) (config-directories state-dir))]
      (when (or denied-config?
                (not-any? #(path-inside? % file-path) directories))
        (path-outside-error file-path)))))
