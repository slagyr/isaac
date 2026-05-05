(ns isaac.module.coords
  (:refer-clojure :exclude [resolve])
  (:require
    [isaac.fs :as fs]))

(def id-pattern #"[a-zA-Z0-9._-]+")

(defn valid-id? [id]
  (and (keyword? id)
       (boolean (re-matches id-pattern (name id)))))

(defn- id-segment [id]
  (if (valid-id? id)
    (name id)
    (throw (ex-info (str "invalid module id: " (pr-str id))
                    {:type    :module/invalid-id
                     :id      id
                     :segment (when (keyword? id) (name id))}))))

(defn candidates
  [{:keys [cwd state-dir] :or {cwd (System/getProperty "user.dir")}} id]
  (let [segment (id-segment id)
        built-in (str cwd "/modules/" segment)]
    (cond-> []
      state-dir (conj (str state-dir "/modules/" segment))
      true      (conj built-in))))

(defn resolve [context id]
  (some #(when (fs/dir? %) %) (candidates context id)))
