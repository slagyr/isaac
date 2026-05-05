(ns isaac.module.loader
  (:require
    [babashka.classpath :as cp]
    [c3kit.apron.schema :as cs]
    [clojure.edn :as edn]
    [isaac.fs :as fs]
    [isaac.logger :as log]
    [isaac.module.coords :as coords]
    [isaac.module.manifest :as manifest]))

(defonce ^:private activated-modules* (atom #{}))

(defn- ->module-id [raw]
  (cond
    (keyword? raw) raw
    (symbol? raw)  (keyword (str raw))
    (string? raw)  (keyword raw)
    :else          nil))

(defn- id-str [id]
  (name id))

(defn- mod-error-key [id]
  (str "modules[\"" (id-str id) "\"]"))

(defn- manifest-error-key [id field]
  (str "module-index[\"" (id-str id) "\"]." (name field)))

(defn- manifest-errors [id result]
  (mapv (fn [[field msg]]
          {:key   (manifest-error-key id field)
           :value msg})
        (cs/message-map result)))

(defn- read-manifest-edn [path]
  (try
    (edn/read-string (fs/slurp path))
    (catch Exception _ nil)))

(defn- discover-one [context id]
  (if-let [path (coords/resolve context id)]
    (let [manifest-path (str path "/module.edn")
          raw           (read-manifest-edn manifest-path)]
      (if-not (map? raw)
        {:errors [{:key (mod-error-key id) :value "manifest: could not read"}]}
        (let [result (cs/conform manifest/manifest-schema raw)]
          (if (cs/error? result)
            {:errors (manifest-errors id result)}
            {:entry {id {:manifest result
                         :path     (str "modules/" (id-str id))
                         :dir      path}}}))))
    {:errors [{:key (mod-error-key id) :value "module directory not found"}]}))

(defn- cycle-errors [index]
  (let [id->requires (into {} (map (fn [[id e]] [id (get-in e [:manifest :requires] [])]) index))
        white        (atom (set (keys id->requires)))
        gray         (atom #{})
        found        (atom [])]
    (letfn [(dfs [node]
              (swap! white disj node)
              (swap! gray conj node)
              (doseq [req (get id->requires node [])]
                (when (contains? id->requires req)
                  (cond
                    (contains? @gray req)
                    (swap! found conj {:key   (str "modules[\"" (id-str req) "\"]")
                                       :value (str "requires cycle detected involving " (id-str req))})
                    (contains? @white req)
                    (dfs req))))
              (swap! gray disj node))]
      (doseq [node (keys id->requires)]
        (when (contains? @white node)
          (dfs node)))
       @found)))

(defn clear-activations! []
  (reset! activated-modules* #{}))

(defn activate! [module-id module-index]
  (let [id    (or (->module-id module-id) module-id)
        entry (get-in module-index [id :manifest :entry])]
    (cond
      (contains? @activated-modules* id)
      :already-active

      (nil? entry)
      (let [error (ex-info (str "module activation failed: " (id-str id))
                           {:type      :module/activation-failed
                            :module-id id
                            :entry     entry
                            :reason    :missing-entry})]
        (log/error :module/activation-failed :module (id-str id) :reason :missing-entry)
        (throw error))

      :else
      (try
        (when-let [dir (get-in module-index [id :dir])]
          (let [src (str dir "/src")]
            (when (.isDirectory (java.io.File. src))
              (cp/add-classpath src))))
        (require entry)
        (swap! activated-modules* conj id)
        (log/info :module/activated :entry (str entry) :module (id-str id))
        :activated
        (catch Exception e
          (let [error (ex-info (str "module activation failed: " (id-str id))
                               {:type      :module/activation-failed
                                :module-id id
                                :entry     entry}
                               e)]
            (log/error :module/activation-failed
                       :entry  (str entry)
                       :error  (.getMessage e)
                       :module (id-str id))
            (throw error)))))))

(defn discover!
  "Reads :modules from config, resolves each to a directory, parses manifests,
   and returns {:index {...} :errors [...]}. No module source code is loaded."
  [config context]
  (let [raw-ids    (get config :modules [])
        module-ids (mapv ->module-id raw-ids)
        {:keys [index errors seen]}
        (reduce (fn [{:keys [index errors seen]} id]
                  (if (nil? id)
                    {:index index :errors errors :seen seen}
                    (if (contains? seen id)
                      {:index  index
                       :errors (conj errors {:key   (mod-error-key id)
                                             :value "duplicate module id"})
                       :seen   seen}
                      (let [{entry :entry mod-errors :errors} (discover-one context id)]
                        {:index  (merge index entry)
                         :errors (into errors (or mod-errors []))
                         :seen   (conj seen id)}))))
                {:index {} :errors [] :seen #{}}
                module-ids)]
    {:index  index
     :errors (into errors (cycle-errors index))}))
