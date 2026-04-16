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
  (-list-files   [fs dir])
  (-make-dirs    [fs path])
  (-delete-file  [fs path]))

;; region ----- RealFs -----

(deftype RealFs []
  Fs
  (-slurp        [_ path options]
    (when (.exists (io/file path))
      (if (seq options)
        (apply clojure.core/slurp path options)
        (clojure.core/slurp path))))
  (-spit         [_ path content options]
    (io/make-parents path)
    (if (seq options)
      (apply clojure.core/spit path content options)
      (clojure.core/spit path content)))
  (-append-file  [_ path content] (io/make-parents path) (spit path content :append true))
  (-exists?      [_ path]         (.exists (io/file path)))
  (-file?        [_ path]         (.isFile (io/file path)))
  (-dir?         [_ path]         (.isDirectory (io/file path)))
  (-children     [_ path]
    (let [f (io/file path)]
      (when (.isDirectory f)
        (some->> (.list f)
                 seq
                 sort
                 vec))) )
  (-list-files   [_ dir]
    (let [f (io/file dir)]
      (when (.isDirectory f)
        (sort (vec (.list f))))))
  (-make-dirs    [_ path]         (io/make-parents path))
  (-delete-file  [_ path]         (.delete (io/file path))))

;; endregion

;; region ----- MemFs -----

(deftype MemFs [store]
  Fs
  (-slurp        [_ path _]       (get @store path))
  (-spit         [_ path content options]
    (if (:append (apply hash-map options))
      (-append-file _ path content)
      (do
        (swap! store #(cond-> (assoc % path content)
                              (parent-path path) (assoc [::dir (parent-path path)] true)))
        nil)))
  (-append-file  [_ path content]
    (swap! store #(cond-> (update % path (fn [existing] (str (or existing "") content)))
                          (parent-path path) (assoc [::dir (parent-path path)] true)))
    nil)
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
  (-list-files   [_ dir]
    (let [prefix (if (str/ends-with? dir "/") dir (str dir "/"))]
      (->> (keys @store)
           (filter string?)
           (filter #(str/starts-with? % prefix))
           (map #(subs % (count prefix)))
           (remove #(str/includes? % "/"))
           sort
           seq)))
  (-make-dirs    [_ path]         (swap! store assoc [::dir path] true) nil)
  (-delete-file  [_ path]         (swap! store dissoc path) nil))

;; endregion

(def ^:dynamic *fs* (->RealFs))

;; region ----- Deprecated API -----

(defn append-file
  ([path content] (-append-file *fs* path content))
  ([fs path content] (-append-file fs path content)))

(defn list-files
  ([dir] (-list-files *fs* dir))
  ([fs dir] (-list-files fs dir)))

(defn make-dirs
  ([path] (-make-dirs *fs* path))
  ([fs path] (-make-dirs fs path)))

(defn delete-file
  ([path] (-delete-file *fs* path))
  ([fs path] (-delete-file fs path)))

;; endregion ^^^^^ Public API ^^^^^


;; region ----- Public API -----

(defn mem-fs [] (->MemFs (atom {})))

(defn exists?
  ([path] (-exists? *fs* path))
  ([fs path] (-exists? fs path)))

(defn file?
  ([path] (-file? *fs* path))
  ([fs path] (-file? fs path)))

(defn dir?
  ([path] (-dir? *fs* path))
  ([fs path] (-dir? fs path)))

(defn parent [path]
  (parent-path path))

(defn children
  ([path] (-children *fs* path))
  ([fs path] (-children fs path)))

(defn slurp
  ([path] (-slurp *fs* path nil))
  ([path & options] (-slurp *fs* path options)))

(defn spit
  ([path content] (-spit *fs* path content nil))
  ([path content & options] (-spit *fs* path content options)))

(defn mkdirs
  ([path] (-make-dirs *fs* path))
  ([fs path] (-make-dirs fs path)))

(defn delete
  ([path] (-delete-file *fs* path))
  ([fs path] (-delete-file fs path)))

;; endregion
