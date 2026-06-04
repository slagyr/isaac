(ns isaac.module.loader
  (:require
    [c3kit.apron.schema :as cs]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [isaac.cli :as cli]
    [isaac.fs :as fs]
    [isaac.llm.api :as api]
    [isaac.logger :as log]
    [isaac.module :as module]
    [isaac.module.manifest :as manifest]
    [isaac.schema.lexicon :as lexicon]
    [isaac.schema.registered-in :as registered-in]))

(defonce ^:private activated-modules* (atom #{}))
(defonce ^:private loaded-module-coords* (atom #{}))
(defonce ^:private started-modules* (atom []))

;; ----- Registry handler injection -----
;; module.loader needs to call into registries (isaac.api, tool.registry,
;; slash.registry, server.routes) and a config-snapshot reader (config.loader)
;; during activation, but those nses transitively require module.loader. To
;; break the cycle, each one self-registers a handler at load time and
;; module.loader dispatches through this table instead of compile-time
;; requires.
(defonce ^:private handlers* (atom {}))

(defn register-handler!
  "Registers a handler fn that module.loader will invoke during activation.
   Called by registry namespaces at their load time.

   Known kinds:
     :comm           (fn [comm-id factory])             — registers a comm impl
     :tools          (fn [spec])                        — registers a tool spec
     :slash-commands (fn [spec])                        — registers a slash command
     :route          (fn [method path handler])         — registers an HTTP route
     :route-prefix   (fn [prefix handler])              — registers an HTTP prefix route
     :user-config    (fn [root-key entry-id] => map)    — reads user config for an extension"
  [kind handler-fn]
  (swap! handlers* assoc kind handler-fn))

(defn- handler-for [kind]
  (or (get @handlers* kind)
      (throw (ex-info (str "no module-loader handler registered for kind " kind
                           " (registry namespace must self-register at load time)")
                      {:kind kind :registered-kinds (vec (sort (keys @handlers*)))}))))

(def ^:private core-module-id :isaac.core)

(defn- runtime-fs []
  (or (fs/instance) (throw (ex-info "module.loader requires :fs in system" {}))))

(declare activate!)
(declare core-index)
(declare register-tool-extension!)

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
  (let [fs* (runtime-fs)]
    (try
      (edn/read-string (if (and (string? path) (fs/exists? fs* path))
                         (fs/slurp fs* path)
                         (slurp path)))
      (catch Exception _ nil))))

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

(defn- ensure-dynamic-classloader!
  "`clojure.repl.deps/add-libs` requires a `DynamicClassLoader` on the
   current thread. Bare `clj -M` doesn't install one, so we wrap whatever
   loader is there. Bb manages its own classpath via `babashka.deps`."
  []
  (let [thread (Thread/currentThread)
        cl    (.getContextClassLoader thread)]
    (when-not (instance? clojure.lang.DynamicClassLoader cl)
      (.setContextClassLoader thread (clojure.lang.DynamicClassLoader. cl)))))

(defn- call-add-libs! [add-libs lib coord]
  ;; `clojure.repl.deps/add-libs` is gated on `clojure.core/*repl*` being
  ;; truthy — it's documented as REPL-only. We bind it around the call so
  ;; isaac can pull config-declared modules in a plain `clj -M` server too.
  (binding [clojure.core/*repl* true]
    (ensure-dynamic-classloader!)
    (add-libs {lib coord})))

(defn- add-module-deps! [id coord]
  (let [lib          (->lib-sym id)
        bb-add-deps  (try (requiring-resolve 'babashka.deps/add-deps)
                          (catch Exception _ nil))
        clj-add-libs (try (requiring-resolve 'clojure.repl.deps/add-libs)
                          (catch Exception _ nil))]
    (cond
      bb-add-deps
      (bb-add-deps {:deps {lib coord}})

      clj-add-libs
      (try
        (call-add-libs! clj-add-libs lib coord)
        (catch Exception e
          (log/warn :module/add-libs-failed
                    :module  id
                    :coord   coord
                    :error   (.getMessage e))))

      :else
      (log/warn :module/no-add-deps-mechanism :module id))))

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

(defn- local-manifest-path [root fs*]
  (some #(when (fs/exists? fs* %) %)
         [(str root "/resources/isaac-manifest.edn")
          (str root "/src/isaac-manifest.edn")]))

(defn resolve-manifest-resource [id coord]
  (let [fs* (runtime-fs)]
    (or (when-let [root (:local/root coord)]
          (when-not (fs/exists? fs* (str root "/deps.edn"))
            (local-manifest-path root fs*)))
        (do
          (when (seq coord)
            (ensure-module-deps! id coord))
          (manifest-resource id)))))

(defn- loadable-coord [context coord]
  (if-let [root (local-root-path context coord)]
    (assoc coord :local/root root)
    coord))

(defn- local-root-error [context id coord]
  (when-let [declared-path (:local/root coord)]
    (let [root (local-root-path context coord)
          fs*  (runtime-fs)]
      (cond
        (not (string? declared-path))
        {:key (mod-error-key id) :value "local/root must be a string"}

        (not (or (real-dir? root) (fs/dir? fs* root)))
        {:key (mod-error-key id) :value "local/root path does not resolve"}))))

(defn- discover-resolved [id coord path]
  (try
    (let [fs*      (runtime-fs)
          resource (resolve-manifest-resource id coord)]
      (if (nil? resource)
        {:errors [{:key (mod-error-key id) :value "manifest: could not read"}]}
        {:entry {id {:coord    coord
                     :manifest (manifest/read-manifest resource fs*)
                     :path     path}}}))
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (cond
          ;; Custom manifest validators (see isaac.module.manifest) emit
          ;; pre-formatted error rows under this key so they can carry the
          ;; exact module-index["id"]... key shape c3kit's nested
          ;; message-map can't reproduce cleanly.
          (:isaac/manifest-errors data)
          {:errors (:isaac/manifest-errors data)}

          (cs/error? data)
          {:errors (manifest-errors id data)}

          :else
          {:errors [{:key (mod-error-key id) :value (.getMessage e)}]})))
    (catch Exception e
      {:errors [{:key (mod-error-key id) :value (.getMessage e)}]})))

(defn- discover-one [context id coord]
  (cond
    ;; Route the core module through `core-index` so the override seam
    ;; (`*core-index-override*`) is the single source of truth — instead
    ;; of having `discover!` re-resolve isaac-manifest.edn from disk.
    (= core-module-id id)
    (if-let [entry (get (core-index) core-module-id)]
      {:entry {core-module-id entry}}
      {:errors [{:key (mod-error-key id) :value "manifest: could not read"}]})

    :else
    (if-let [error (local-root-error context id coord)]
      {:errors [error]}
      (discover-resolved id (loadable-coord context coord) (:local/root coord)))))

(defn- cycle-errors [index]
  (let [id->requires (into {} (map (fn [[id e]] [id (keys (get-in e [:manifest :deps] {}))]) index))
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
                                       :value (str "dependency cycle detected involving " (id-str req))})

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
            (when (get-in entry [:manifest kind cap-key])
              module-id))
          module-index)))

(defonce ^:private core-index-cache (atom nil))

;; When bound, replaces the resource-loaded core manifest index.
;; Tests use this to swap in a themed manifest; see spec/isaac/marigold.clj.
(def ^:dynamic *core-index-override* nil)

(defn clear-activations! []
  (reset! core-index-cache nil)
  (reset! activated-modules* #{})
  (reset! started-modules* [])
  (api/clear-module-registrations!)
  (cli/clear-module-commands!))

(defn clear-caches! []
  (reset! core-index-cache nil))

(defn core-index []
  (or *core-index-override*
      @core-index-cache
      (let [result (if-let [resource (manifest-resource core-module-id)]
                     (let [manifest (manifest/read-manifest resource (runtime-fs))]
                        {core-module-id {:coord {} :manifest manifest :path nil}})
                      {})]
        (reset! core-index-cache result)
        result)))

(defn comm-kinds
  "Returns sorted user-configurable comm kind names from the given module index.
   Filters out entries where :configurable? is false. With no args, falls back
   to the core manifest index."
  ([] (comm-kinds (core-index)))
  ([module-index]
   (->> (vals module-index)
        (mapcat #(get-in % [:manifest :comm]))
        (remove (fn [[_ v]] (false? (:configurable? v))))
        (map (fn [[k _]] (name k)))
        sort
        distinct
        vec)))

(defn activate-core! []
  (activate! core-module-id (core-index)))

(defn deactivate-core! []
  (swap! activated-modules* disj core-module-id))

(defn register-core-tool! [tool-id]
  (when-let [extension (get-in (core-index) [core-module-id :manifest :tools (keyword tool-id)])]
    (register-tool-extension! (keyword tool-id) extension)))

(defn- resolve-symbol! [sym]
  (requiring-resolve sym))

(defn- user-config [root-key entry-id]
  (or ((handler-for :user-config) root-key entry-id) {}))

(defn- register-api-extension! [api-id extension]
  (api/register! api-id (resolve-symbol! (:factory extension))))

(defn- register-comm-extension! [comm-id extension]
  ((handler-for :comm) (name comm-id) (resolve-symbol! (:factory extension))))

(defn- register-tool-extension! [tool-id extension]
  (let [tool-name (name tool-id)
        factory   (resolve-symbol! (:factory extension))
        spec      (factory (user-config :tools tool-name))]
    ((handler-for :tools) (assoc spec :name tool-name))))

(defn register-cli-extension! [_cli-id extension]
  (let [factory (resolve-symbol! (:factory extension))
        spec    (cond-> (factory)
                  (:description extension) (assoc :desc (:description extension)))]
    (cli/register-module-command! spec)))

(defn- register-slash-extension! [command-id extension]
  (let [command-id (name command-id)
        factory    (resolve-symbol! (:factory extension))
        spec       (factory (user-config :slash-commands command-id))]
    ((handler-for :slash-commands)
     {:name        (:command-name spec)
      :description (:description spec)
      :handler     (:handler spec)})))

(defn register-route-extensions! [manifest]
  (doseq [[[method path] handler] (:route manifest)]
    (let [resolved-handler (resolve-symbol! handler)]
      (if (str/ends-with? path "/*")
        ((handler-for :route-prefix)
         (subs path 0 (dec (count path)))
         resolved-handler)
        ((handler-for :route) method path resolved-handler)))))

(defn- register-extensions! [manifest]
  (doseq [kind [:llm/api :comm :tools :slash-commands :hook :cli]
          [extension-id extension] (get manifest kind)]
    (case kind
      :llm/api        (register-api-extension! extension-id extension)
      :comm           (register-comm-extension! extension-id extension)
      :tools          (register-tool-extension! extension-id extension)
      :slash-commands (register-slash-extension! extension-id extension)
      :hook           nil
      :cli            (register-cli-extension! extension-id extension)))
  (register-route-extensions! manifest))

(defn- call-bootstrap! [bootstrap]
  (when bootstrap
    ((resolve-symbol! bootstrap))))

(defn activate! [module-id module-index]
  (let [id          (or (->module-id module-id) module-id)
        module-meta (get module-index id)
        manifest    (:manifest module-meta)
        bootstrap   (:bootstrap manifest)
        coord       (:coord module-meta)]
    (cond
      (contains? @activated-modules* id)
      :already-active

      (nil? manifest)
      (let [error (ex-info (str "module activation failed: " (id-str id))
                           {:type      :module/activation-failed
                            :module-id id
                            :bootstrap bootstrap
                            :reason    :missing-manifest})]
        (log/error :module/activation-failed :module (id-str id) :reason :missing-manifest)
        (throw error))

      :else
      (try
        (when (:path module-meta)
          (ensure-module-deps! id coord))
        (register-extensions! manifest)
        (call-bootstrap! bootstrap)
        (swap! activated-modules* conj id)
        (log/info :module/activated :bootstrap (some-> bootstrap str) :module (id-str id))
        :activated
        (catch Exception e
          (let [error (ex-info (str "module activation failed: " (id-str id))
                               {:type      :module/activation-failed
                                :module-id id
                                :bootstrap bootstrap}
                               e)]
            (log/error :module/activation-failed
                       :bootstrap (some-> bootstrap str)
                       :error  (.getMessage e)
                       :module (id-str id))
            (throw error)))))))

;; Top-level manifest keys that are NOT berth contributions: the
;; existing reserved + extension-kind set carried by manifest.clj. Any
;; OTHER namespaced top-level key in a consumer manifest is treated
;; as a contribution and validated against the matching berth's
;; :manifest :schema (see validate-contributions!).
(def ^:private reserved-top-level-keys
  (into @#'manifest/known-meta-keys @#'manifest/known-extend-kinds))

(defn- contribution-key? [k]
  (and (qualified-keyword? k)
       (not (contains? reserved-top-level-keys k))))

(defn- collect-contributions [manifest-map]
  (keep (fn [[k v]]
          (when (contribution-key? k) [k v]))
        manifest-map))

(defn- find-berth-decl [module-index berth-key]
  (some (fn [[_provider-id entry]]
          (get-in entry [:manifest :berths berth-key]))
        module-index))

(defn- ns-keyword->str [kw]
  (str (namespace kw) "/" (name kw)))

(defn- unknown-berth-error [consumer-id berth-key]
  {:key   (str "module-index[\"" (id-str consumer-id) "\"][" berth-key "]")
   :value "berth not declared by any installed module"})

(defn- flatten-error-paths
  "Walk a c3kit message-map (nested keywords → message strings) producing
   flat [path-vec message-string] pairs."
  ([m] (flatten-error-paths m []))
  ([m prefix]
   (cond
     (map? m) (mapcat (fn [[k v]] (flatten-error-paths v (conj prefix k))) m)
     :else    [[prefix (str m)]])))

(defn- format-contribution-suffix
  "First path segment is the contribution-map's outer key (rendered as
   [<kw>]); subsequent segments are dot-prefixed field names. Matches
   the bean's expected shape: berth[:key].field..."
  [path]
  (let [[head & tail] path]
    (str (when head (str "[" head "]"))
         (apply str (map #(str "." (name %)) tail)))))

(defn- berth-lexicon
  "Active lexicon with `:present?` re-messaged for berth contributions —
   apron's default 'is required' becomes 'must be present', which is
   the wording ISAAC surfaces consistently for missing berth fields."
  []
  (-> (@#'lexicon/active-lexicon)
      (assoc-in [:validations :present?]
                {:validate cs/present? :message "must be present"})))

(defn- contribution-validation-errors [consumer-id berth-key value berth-schema]
  (let [prefix (str "module-index[\"" (id-str consumer-id) "\"]."
                    (ns-keyword->str berth-key))
        result (try (binding [cs/*lexicon* (berth-lexicon)]
                      (cs/conform berth-schema value))
                    (catch Throwable _ nil))]
    (when (and result (cs/error? result))
      (->> (cs/message-map result)
           flatten-error-paths
           (mapv (fn [[path msg]]
                   {:key   (str prefix (format-contribution-suffix path))
                    :value msg}))))))

(defn- validate-contributions! [module-index]
  ;; Bind *module-index* so berth schemas using the :registered-in?
  ;; primitive can resolve sibling contributions across the loaded set
  ;; (the validator is data-only; the foundation supplies the view).
  (binding [registered-in/*module-index* module-index]
    (vec
      (mapcat
        (fn [[consumer-id entry]]
          (mapcat
            (fn [[berth-key value]]
              (if-let [berth-decl (find-berth-decl module-index berth-key)]
                (contribution-validation-errors consumer-id berth-key value
                                                (get-in berth-decl [:manifest :schema]))
                [(unknown-berth-error consumer-id berth-key)]))
            (collect-contributions (:manifest entry))))
        module-index))))

(defn- lifecycle-error
  [message data cause]
  (ex-info message (assoc data :type :module/lifecycle-failed) cause))

(defn- lifecycle-deps [module-index module-id]
  (->> (keys (get-in module-index [module-id :manifest :deps] {}))
       (filter #(contains? module-index %))
       (sort-by id-str)))

(defn- cycle-path [stack module-id]
  (conj (vec (drop-while #(not= % module-id) stack)) module-id))

(defn- topological-order [module-index]
  (let [visiting (atom #{})
        visited  (atom #{})
        order    (atom [])]
    (letfn [(visit [module-id stack]
              (cond
                (contains? @visited module-id)
                nil

                (contains? @visiting module-id)
                (let [cycle   (cycle-path stack module-id)
                      message (str "module dependency cycle detected: "
                                   (str/join " -> " (map id-str cycle)))]
                  (throw (lifecycle-error message
                                          {:reason    :dependency-cycle
                                           :module-id module-id
                                           :cycle     cycle}
                                          nil)))

                :else
                (do
                  (swap! visiting conj module-id)
                  (doseq [dep (lifecycle-deps module-index module-id)]
                    (visit dep (conj stack module-id)))
                  (swap! visiting disj module-id)
                  (swap! visited conj module-id)
                  (swap! order conj module-id))))]
      (doseq [module-id (sort-by id-str (keys module-index))]
        (visit module-id []))
      @order)))

(defn- resolve-module-factory! [module-id factory-sym]
  (try
    (resolve-symbol! factory-sym)
    (catch Exception e
      (throw (lifecycle-error (str "module factory resolution failed for " (id-str module-id)
                                   ": " factory-sym)
                              {:reason    :resolve-factory
                               :module-id module-id
                               :factory   factory-sym}
                              e)))))

(defn- instantiate-module! [module-id {:keys [manifest]}]
  (let [factory-sym (:factory manifest)
        factory     (resolve-module-factory! module-id factory-sym)
        instance    (try
                      (factory)
                      (catch Exception e
                        (throw (lifecycle-error (str "module factory threw for " (id-str module-id))
                                                {:reason    :factory-threw
                                                 :module-id module-id
                                                 :factory   factory-sym}
                                                e))))]
    (when-not (module/module? instance)
      (throw (lifecycle-error (str "module factory returned non-Module for " (id-str module-id))
                              {:reason    :not-a-module
                               :module-id module-id
                               :factory   factory-sym
                               :value-type (some-> instance class str)}
                              nil)))
    instance))

(defn- rollback-started-modules! [started]
  (doseq [{:keys [id instance]} (reverse started)]
    (try
      (module/run-shutdown! instance)
      (catch Exception e
        (log/error :module/shutdown-failed
                   :error  (.getMessage e)
                   :module (id-str id))))))

(defn start-modules! [module-index]
  (let [order     (topological-order module-index)
        instances (mapv (fn [module-id]
                          {:id       module-id
                           :instance (instantiate-module! module-id (get module-index module-id))})
                        order)
        started   (atom [])]
    (reset! started-modules* [])
    (try
      (doseq [{:keys [id instance] :as started-module} instances]
        (try
          (module/run-startup! instance)
          (swap! started conj started-module)
          (catch Exception e
            (throw (lifecycle-error (str "module startup failed for " (id-str id))
                                    {:reason    :startup-failed
                                     :module-id id}
                                    e)))))
      (reset! started-modules* @started)
      :started
      (catch Exception e
        (rollback-started-modules! @started)
        (reset! started-modules* [])
        (throw e)))))

(defn shutdown-modules! []
  (rollback-started-modules! @started-modules*)
  (reset! started-modules* [])
  :stopped)

(defn- pending-deps
  "Pairs of [consumer-id dep-id coord] for deps not yet in `index`."
  [index]
  (mapcat (fn [[consumer-id entry]]
            (let [deps (get-in entry [:manifest :deps])]
              (when (map? deps)
                (keep (fn [[dep-id coord]]
                        (when (and (not (contains? index dep-id))
                                   (map? coord))
                          [consumer-id dep-id coord]))
                      deps))))
          index))

(defn- dep-resolution-error [consumer-id dep-id]
  {:key   (str "module-index[\"" (id-str consumer-id) "\"].deps[" dep-id "]")
   :value "failed to resolve coordinate"})

(defn- resolve-deps!
  "Iteratively walks each loaded manifest's `:deps` and resolves any
   modules not already in `index` (delegating to discover-one, which
   routes through `tools.deps`/bb internals for non-local-root coords).
   Closes over the transitive set; reports each failed resolution as
   `module-index[\"<consumer>\"].deps[<dep-id>]` so the user can see
   which consumer dragged the offending dep in. Index membership
   doubles as a cycle guard — A → B → A stops when B sees A already
   resolved."
  [context initial-index]
  (loop [index  initial-index
         errors []]
    (let [pending (pending-deps index)]
      (if (empty? pending)
        {:index index :errors errors}
        (let [{:keys [new-entries new-errors]}
              (reduce
                (fn [{:keys [new-entries new-errors]} [consumer-id dep-id coord]]
                  (cond
                    (contains? index dep-id)        {:new-entries new-entries
                                                     :new-errors  new-errors}
                    (contains? new-entries dep-id)  {:new-entries new-entries
                                                     :new-errors  new-errors}
                    :else
                    (let [{:keys [entry] mod-errors :errors} (discover-one context dep-id coord)]
                      (if (seq mod-errors)
                        {:new-entries new-entries
                         :new-errors  (conj new-errors (dep-resolution-error consumer-id dep-id))}
                        {:new-entries (merge new-entries entry)
                         :new-errors  new-errors}))))
                {:new-entries {} :new-errors []}
                pending)]
          (if (empty? new-entries)
            ;; No forward progress — stop. Any remaining unresolved deps
            ;; landed in new-errors this pass.
            {:index index :errors (into errors new-errors)}
            (recur (merge index new-entries) (into errors new-errors))))))))

(defn discover!
  "Resolves module coordinates from config :modules and returns
   {:index {...} :errors [...]}."
  [config context]
  (let [declared    (get config :modules {})
        raw-modules (merge {core-module-id {}}
                           (when (map? declared) declared))]
    (if (and (some? declared) (not (map? declared)))
      {:index  (core-index)
       :errors [{:key "modules"
                 :value "must be a map of id to coordinate (legacy vector shape)"}]}
      (let [{init-index :index init-errors :errors}
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
                       raw-modules)
            {:keys [index errors]} (resolve-deps! context init-index)]
        {:index  index
         :errors (into (into init-errors errors)
                       (concat (cycle-errors index)
                               (validate-contributions! index)))}))))
