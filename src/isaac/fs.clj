(ns isaac.fs
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]))

(defn- parent-path [path]
  (let [trimmed-path (if (and (str/ends-with? path "/") (> (count path) 1))
                       (subs path 0 (dec (count path)))
                       path)
        parts (str/split trimmed-path #"/")]
    (some->> (butlast parts)
             seq
             (str/join "/"))))

(defn- mem-dir? [store path]
  (let [prefix (str path "/")]
    (or (contains? store [::dir path])
        (some #(str/starts-with? % prefix)
              (keys store)))))

(defprotocol Fs
  (-slurp        [fs path options])
  (-spit         [fs path content options])
  (-exists?      [fs path])
  (-file?        [fs path])
  (-dir?         [fs path])
  (-children     [fs path])
  (-mkdirs       [fs path])
  (-delete       [fs path]))

;; region ----- RealFs -----

(deftype RealFs []
  Fs
  (-slurp        [_ path options]
    (when (.exists (io/file path))
      (if (seq options)
        (apply clojure.core/slurp path options)
        (clojure.core/slurp path))))
  (-spit         [_ path content options]
    (if (seq options)
      (apply clojure.core/spit path content options)
      (clojure.core/spit path content)))
  (-exists?      [_ path]         (.exists (io/file path)))
  (-file?        [_ path]         (.isFile (io/file path)))
  (-dir?         [_ path]         (.isDirectory (io/file path)))
  (-children     [_ path]
    (let [f (io/file path)]
      (when (.isDirectory f)
        (some->> (.list f)
                 seq
                 sort
                 vec))))
  (-mkdirs       [_ path]         (.mkdirs (io/file path)))
  (-delete       [_ path]         (.delete (io/file path))))

;; endregion

;; region ----- MemFs -----

(deftype MemFs [store]
  Fs
  (-slurp        [_ path _]       (get @store path))
  (-spit         [_ path content options]
    (if (:append (apply hash-map options))
      (do
        (swap! store #(cond-> (update % path (fn [existing] (str (or existing "") content)))
                              (parent-path path) (assoc [::dir (parent-path path)] true)))
        nil)
      (do
        (swap! store #(cond-> (assoc % path content)
                              (parent-path path) (assoc [::dir (parent-path path)] true)))
        nil)))
  (-exists?      [_ path]         (or (contains? @store path) (mem-dir? @store path)))
  (-file?        [_ path]         (contains? @store path))
  (-dir?         [_ path]         (mem-dir? @store path))
  (-children     [_ path]
    (when (mem-dir? @store path)
      (let [prefix (if (str/ends-with? path "/") path (str path "/"))]
        (->> (keys @store)
             (map #(cond
                     (string? %) %
                     (and (vector? %) (= ::dir (first %))) (second %)
                     :else nil))
             (keep identity)
             (filter #(str/starts-with? % prefix))
             (map #(subs % (count prefix)))
             (remove str/blank?)
             (map #(first (str/split % #"/")))
             distinct
             sort
             vec))))
  (-mkdirs       [_ path]         (swap! store assoc [::dir path] true) nil)
  (-delete       [_ path]         (swap! store dissoc path) nil))

;; endregion

(def ^:dynamic *fs* (->RealFs))

(defn- assert-absolute! [path]
  (when-not (str/starts-with? path "/")
    (throw (IllegalArgumentException. (str "Relative path not allowed: " path)))))

;; region ----- Public API -----

(defn mem-fs
  "Creates an in-memory filesystem implementation for tests and isolated workflows."
  []
  (->MemFs (atom {})))

(defn exists?
  "Returns truthy when the path exists in the active filesystem, or in the provided filesystem."
  ([path] (assert-absolute! path) (-exists? *fs* path))
  ([fs path] (assert-absolute! path) (-exists? fs path)))

(defn file?
  "Returns truthy when the path refers to a file in the active filesystem, or in the provided filesystem."
  ([path] (assert-absolute! path) (-file? *fs* path))
  ([fs path] (assert-absolute! path) (-file? fs path)))

(defn dir?
  "Returns truthy when the path refers to a directory in the active filesystem, or in the provided filesystem."
  ([path] (assert-absolute! path) (-dir? *fs* path))
  ([fs path] (assert-absolute! path) (-dir? fs path)))

(defn parent
  "Returns the parent path string for the given path, or nil when there is no parent."
  [path]
  (parent-path path))

(defn children
  "Returns a sorted vector of immediate child names for a directory, or nil when the path is not a directory."
  ([path] (assert-absolute! path) (-children *fs* path))
  ([fs path] (assert-absolute! path) (-children fs path)))

(defn slurp
  "Reads and returns file content from the active filesystem.

  Options:
  - :encoding  character encoding name to use when reading."
  ([path] (assert-absolute! path) (-slurp *fs* path nil))
  ([path & options] (assert-absolute! path) (-slurp *fs* path options)))

(defn spit
  "Writes content to a file in the active filesystem.

  Options:
  - :append    when truthy, appends instead of overwriting
  - :encoding  character encoding name to use when writing"
  ([path content] (assert-absolute! path) (-spit *fs* path content nil))
  ([path content & options] (assert-absolute! path) (-spit *fs* path content options)))

(defn mkdirs
  "Creates the directory path in the active filesystem."
  [path]
  (assert-absolute! path)
  (-mkdirs *fs* path))

(defn delete
  "Deletes the path from the active filesystem."
  [path]
  (assert-absolute! path)
  (-delete *fs* path))

(defn copy-tree!
  "Recursively copies `path` from `source-fs` to `target-fs`. Useful
  for staging a dry-run of filesystem changes (e.g. copy real fs into
  a mem-fs, apply edits, validate before committing)."
  [source-fs target-fs path]
  (binding [*fs* source-fs]
    (when (exists? path)
      (if (file? path)
        (let [content (slurp path)
              p      (parent-path path)]
          (binding [*fs* target-fs]
            (when p (mkdirs p))
            (spit path content)))
        (do
          (binding [*fs* target-fs] (mkdirs path))
          (doseq [child (or (children path) [])]
            (copy-tree! source-fs target-fs (str path "/" child))))))))

;; endregion
