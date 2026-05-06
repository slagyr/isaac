(ns isaac.module.loader
  (:require
    [c3kit.apron.schema :as cs]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.fs :as fs]
    [isaac.logger :as log]
    [isaac.module.manifest :as manifest]))

(defonce ^:private activated-modules* (atom #{}))
(defonce ^:private loaded-module-coords* (atom #{}))

(defn- ->module-id [raw]
  (cond
    (keyword? raw) raw
    (symbol? raw)  (keyword (str raw))
    (string? raw)  (keyword raw)
    :else          nil))

(defn- id-str [id]
  (cond
    (keyword? id) (subs (str id) 1)
    (symbol? id)  (str id)
    (string? id)  id
    :else         (str id)))

(defn- ->lib-sym [id]
  (let [s (id-str id)]
    (if (str/includes? s "/")
      (symbol s)
      (symbol s s))))

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
    (edn/read-string (if (and (string? path) (fs/exists? path))
                       (fs/slurp path)
                       (slurp path)))
    (catch Exception _ nil)))

(defn- abs-path [cwd path]
  (if (or (str/starts-with? path "/")
          (re-matches #"[A-Za-z]:.*" path))
    path
    (str cwd "/" path)))

(defn- local-root-path [context coord]
  (when-let [root (:local/root coord)]
    (abs-path (:cwd context) root)))

(defn- real-dir? [path]
  (.isDirectory (java.io.File. path)))

(defn- add-module-deps! [id coord]
  (let [lib (->lib-sym id)]
    (if-let [bb-add-deps (try (requiring-resolve 'babashka.deps/add-deps)
                              (catch Exception _ nil))]
      (bb-add-deps {:deps {lib coord}})
      (let [clj-add-libs (requiring-resolve 'clojure.tools.deps.alpha.repl/add-libs)]
        (clj-add-libs {lib coord})))))

(defn- ensure-module-deps! [id coord]
  (let [key [id coord]
        add? (atom false)]
    (swap! loaded-module-coords*
           (fn [loaded]
             (if (contains? loaded key)
               loaded
               (do
                 (reset! add? true)
                 (conj loaded key)))))
    (when @add?
      (add-module-deps! id coord))))

(defn- resource-urls [resource-name]
  (let [loader (or (.getContextClassLoader (Thread/currentThread))
                   (clojure.lang.RT/baseLoader))]
    (enumeration-seq (.getResources loader resource-name))))

(defn- manifest-resource [id]
  (some (fn [url]
          (when (= id (:id (read-manifest-edn url)))
            url))
        (resource-urls "isaac-manifest.edn")))

(defn- loadable-coord [context coord]
  (if-let [root (local-root-path context coord)]
    (assoc coord :local/root root)
    coord))

(defn- discover-local-root [context id coord]
  (let [declared-path (:local/root coord)
        root          (local-root-path context coord)]
    (cond
      (not (string? declared-path))
      {:errors [{:key (mod-error-key id) :value "local/root must be a string"}]}

      (not (or (real-dir? root) (fs/dir? root)))
      {:errors [{:key (mod-error-key id) :value "local/root path does not resolve"}]}

      :else
      (do
        (let [resolved-coord (loadable-coord context coord)
              manifest-path (str root "/resources/isaac-manifest.edn")
              raw           (read-manifest-edn manifest-path)]
          (if-not (map? raw)
            {:errors [{:key (mod-error-key id) :value "manifest: could not read"}]}
            (let [result (cs/conform manifest/manifest-schema raw)]
              (if (cs/error? result)
                {:errors (manifest-errors id result)}
                {:entry {id {:coord    resolved-coord
                             :manifest result
                             :path     declared-path}}}))))))))

(defn- discover-resolved [context id coord]
  (try
    (add-module-deps! id coord)
    (let [resource (manifest-resource id)]
      (cond
        (nil? resource)
        {:errors [{:key (mod-error-key id) :value "manifest: could not read"}]}

        :else
        (let [raw (read-manifest-edn resource)]
          (if-not (map? raw)
            {:errors [{:key (mod-error-key id) :value "manifest: could not read"}]}
            (let [result (cs/conform manifest/manifest-schema raw)]
              (if (cs/error? result)
                {:errors (manifest-errors id result)}
                {:entry {id {:coord    coord
                             :manifest result
                             :path     nil}}}))))))
    (catch Exception e
      {:errors [{:key (mod-error-key id) :value (.getMessage e)}]})))

(defn- discover-one [context id coord]
  (if (:local/root coord)
    (discover-local-root context id coord)
    (discover-resolved context id (loadable-coord context coord))))

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

(defn supporting-module-id [module-index kind capability]
  (let [cap-key (cond
                  (keyword? capability) capability
                  (string? capability)  (keyword capability)
                  :else                 (keyword (str capability)))]
    (some (fn [[module-id entry]]
            (when (get-in entry [:manifest :extends kind cap-key])
              module-id))
          module-index)))

(defn clear-activations! []
  (reset! activated-modules* #{}))

(defn- call-isaac-init! [entry]
  (when-let [init-fn (find-var (symbol (str entry) "-isaac-init"))]
    (init-fn)))

(defn activate! [module-id module-index]
  (let [id          (or (->module-id module-id) module-id)
        module-meta (get module-index id)
        entry       (get-in module-meta [:manifest :entry])
        coord       (:coord module-meta)]
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
        (when (:path module-meta)
          (ensure-module-deps! id coord))
        (require entry :reload)
        (call-isaac-init! entry)
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

(defn- built-in-module-coords [cwd]
  (when cwd
    (let [mods-dir (str cwd "/modules")]
      (when (fs/dir? mods-dir)
        (->> (fs/children mods-dir)
             (filter (fn [name] (fs/dir? (str mods-dir "/" name))))
             (reduce (fn [acc name]
                       (assoc acc (keyword name)
                              {:local/root (str mods-dir "/" name)}))
                     {}))))))

(defn discover!
  "Resolves module coordinates from config :modules (merged with built-in modules
   under {cwd}/modules/) and returns {:index {...} :errors [...]}."
  [config context]
  (let [declared    (get config :modules {})
        built-in    (built-in-module-coords (:cwd context))
        raw-modules (merge (when (map? built-in) built-in)
                           (when (map? declared) declared))]
    (if-not (map? raw-modules)
      {:index {} :errors []}
      (let [{:keys [index errors]}
            (reduce-kv (fn [{:keys [index errors]} raw-id coord]
                         (let [id (->module-id raw-id)]
                           (if (or (nil? id) (not (map? coord)))
                             {:index  index
                              :errors (conj errors {:key   (mod-error-key (or id raw-id))
                                                    :value "invalid coordinate"})}
                             (let [{entry :entry mod-errors :errors} (discover-one context id coord)]
                               {:index  (merge index entry)
                                :errors (into errors (or mod-errors []))}))))
                       {:index {} :errors []}
                       raw-modules)]
        {:index  index
         :errors (into errors (cycle-errors index))}))))
