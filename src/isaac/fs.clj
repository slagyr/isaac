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
  (-read-file    [fs path])
  (-write-file   [fs path content])
  (-append-file  [fs path content])
  (-file-exists? [fs path])
  (-exists?      [fs path])
  (-file?        [fs path])
  (-dir?         [fs path])
  (-list-files   [fs dir])
  (-make-dirs    [fs path])
  (-delete-file  [fs path]))

;; region ----- RealFs -----

(defrecord RealFs []
  Fs
  (-read-file    [_ path]         (slurp path))
  (-write-file   [_ path content] (io/make-parents path) (spit path content))
  (-append-file  [_ path content] (io/make-parents path) (spit path content :append true))
  (-file-exists? [_ path]         (.exists (io/file path)))
  (-exists?      [_ path]         (.exists (io/file path)))
  (-file?        [_ path]         (.isFile (io/file path)))
  (-dir?         [_ path]         (.isDirectory (io/file path)))
  (-list-files   [_ dir]
    (let [f (io/file dir)]
      (when (.isDirectory f)
        (sort (vec (.list f))))))
  (-make-dirs    [_ path]         (io/make-parents path))
  (-delete-file  [_ path]         (.delete (io/file path))))

;; endregion

;; region ----- MemFs -----

(defrecord MemFs [store]
  Fs
  (-read-file    [_ path]         (get @store path))
  (-write-file   [_ path content]
    (swap! store #(cond-> (assoc % path content)
                          (parent-path path) (assoc [::dir (parent-path path)] true)))
    nil)
  (-append-file  [_ path content]
    (swap! store #(cond-> (update % path (fn [existing] (str (or existing "") content)))
                          (parent-path path) (assoc [::dir (parent-path path)] true)))
    nil)
  (-file-exists? [_ path]         (contains? @store path))
  (-exists?      [_ path]         (or (contains? @store path) (mem-dir? @store path)))
  (-file?        [_ path]         (contains? @store path))
  (-dir?         [_ path]         (mem-dir? @store path))
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

;; region ----- Public API -----

(defn read-file
  ([path] (-read-file *fs* path))
  ([fs path] (-read-file fs path)))

(defn write-file
  ([path content] (-write-file *fs* path content))
  ([fs path content] (-write-file fs path content)))

(defn append-file
  ([path content] (-append-file *fs* path content))
  ([fs path content] (-append-file fs path content)))

(defn file-exists?
  ([path] (-file-exists? *fs* path))
  ([fs path] (-file-exists? fs path)))

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

(defn list-files
  ([dir] (-list-files *fs* dir))
  ([fs dir] (-list-files fs dir)))

(defn make-dirs
  ([path] (-make-dirs *fs* path))
  ([fs path] (-make-dirs fs path)))

(defn delete-file
  ([path] (-delete-file *fs* path))
  ([fs path] (-delete-file fs path)))

(defn mem-fs [] (->MemFs (atom {})))

;; Proposed api.
;(parent path)
;
;(slurp path)
;(slurp path options) :encoding
;(spit path content)
;(spit path content options) :encoding, :append
;
;(children path)
;(mkdirs path)
;
;(delete path)

;; endregion
