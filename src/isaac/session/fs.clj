(ns isaac.session.fs
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]))

;; TODO - MDM: fs is not a component of session.  Move this namespace to isaac.fs

(defprotocol Fs
  (read-file    [fs path])
  (write-file   [fs path content])
  (append-file  [fs path content])
  (file-exists? [fs path])
  (list-files   [fs dir])
  (make-dirs    [fs path])
  (delete-file  [fs path]))

;; region ----- RealFs -----

(defrecord RealFs []
  Fs
  (read-file    [_ path]         (slurp path))
  (write-file   [_ path content] (io/make-parents path) (spit path content))
  (append-file  [_ path content] (io/make-parents path) (spit path content :append true))
  (file-exists? [_ path]         (.exists (io/file path)))
  (list-files   [_ dir]
    (let [f (io/file dir)]
      (when (.isDirectory f)
        (vec (.list f)))))
  (make-dirs    [_ path]         (io/make-parents path))
  (delete-file  [_ path]        (.delete (io/file path))))

;; endregion

;; region ----- MemFs -----

(defrecord MemFs [store]
  Fs
  (read-file    [_ path]         (get @store path))
  (write-file   [_ path content] (swap! store assoc path content) nil)
  (append-file  [_ path content] (swap! store update path #(str (or % "") content)) nil)
  (file-exists? [_ path]         (contains? @store path))
  (list-files   [_ dir]
    (let [prefix (if (str/ends-with? dir "/") dir (str dir "/"))]
      (->> (keys @store)
           (filter #(str/starts-with? % prefix))
           (map #(subs % (count prefix)))
           (remove #(str/includes? % "/"))
           sort
           seq)))
  (make-dirs    [_ _]            nil)
  (delete-file  [_ path]        (swap! store dissoc path) nil))

;; endregion

;; region ----- Default -----

(def ^:dynamic *fs* (->RealFs))

(defn mem-fs [] (->MemFs (atom {})))

;; endregion
