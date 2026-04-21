(ns isaac.config.loader
  (:require
    [c3kit.apron.env :as c3env]
    [c3kit.apron.schema :as cs]
    [clojure.edn :as edn]
    [clojure.set :as set]
    [clojure.string :as str]
    [isaac.config.paths :as paths]
    [isaac.config.schema :as schema]
    [isaac.fs :as fs]))

;; region ----- Helpers -----

(defonce env-overrides* (atom {}))
(def ^:dynamic *isaac-home* nil)

(defn- isaac-env-path []
  (when *isaac-home*
    (str *isaac-home* "/.env")))

(defn- isaac-env-value [name]
  (when-let [path (isaac-env-path)]
    (when (fs/exists? path)
      (let [props (doto (java.util.Properties.)
                    (.load (java.io.StringReader. (or (fs/slurp path) ""))))]
        (.getProperty props name)))))

(defn clear-env-overrides! []
  (reset! env-overrides* {}))

(defn set-env-override! [name value]
  (swap! env-overrides* assoc name value))

(defn env [name]
  (or (get @env-overrides* name)
      (c3env/env name)
      (isaac-env-value name)))

(def ^:private ->id schema/->id)

(defn- source-path [relative]
  (str "config/" relative))

(defn- missing-config-message [home]
  (str "no config found; create " home "/.isaac/config/isaac.edn"))

(defn- warning [key value]
  {:key key :value value})

(defn- has-ext? [path ext]
  (str/ends-with? path ext))

(defn- substitute-env [s]
  (str/replace s #"\$\{([^}]+)\}" (fn [[match var-name]] (or (env var-name) match))))

(defn- substitute-env-recursive [value]
  (cond
    (string? value)     (substitute-env value)
    (map? value)        (into {} (map (fn [[k v]] [k (substitute-env-recursive v)]) value))
    (sequential? value) (mapv substitute-env-recursive value)
    :else               value))

(defn- read-edn-string [content substitute-env?]
  (-> content
      edn/read-string
      ((fn [value]
         (if substitute-env?
           (substitute-env-recursive value)
           value)))))

(defn- read-edn-file [path substitute-env?]
  (try
    {:data (read-edn-string (fs/slurp path) substitute-env?)}
    (catch Exception _
      {:error "EDN syntax error"})))

(defn- present? [value]
  (and (some? value)
       (not (and (string? value) (str/blank? value)))))

(defn- assoc-error [result key value]
  (update result :errors conj {:key key :value value}))

(defn- normalize-defaults [defaults]
  (let [result (cs/conform schema/defaults defaults)]
    (if (cs/error? result) {} result)))

(defn- normalize-crew [crew]
  (let [result (cs/conform schema/crew crew)]
    (if (cs/error? result) {} result)))

(defn- normalize-model [model]
  (let [result (cs/conform schema/model model)]
    (if (cs/error? result) {} result)))

(defn- collect-unknown-key-warnings [warnings kind id entity entity-schema]
  (let [entity-fields (schema/schema-fields entity-schema)]
    (reduce (fn [acc key]
              (if (contains? entity-fields key)
                acc
                (conj acc (warning (str kind "." id "." (name key)) "unknown key"))))
            warnings
            (keys entity))))

(defn- read-entity-files [root dir-name]
  (let [dir (str root "/" dir-name)]
    (->> (or (fs/children dir) [])
         (filter #(has-ext? % ".edn"))
         sort
         (mapv (fn [name]
                 {:id       (subs name 0 (- (count name) 4))
                  :path     (str dir "/" name)
                  :relative (str dir-name "/" name)})))))


(defn- overlay-relative [{:keys [overlay-path]}]
  (when (present? overlay-path)
    overlay-path))

(defn- overlay-for [opts relative]
  (when (= relative (overlay-relative opts))
    {:path     (str "<overlay>/" relative)
     :relative relative
     :content  (:overlay-content opts)
     :overlay? true}))

(defn- config-files-present? [root opts]
  (or (overlay-relative opts)
      (fs/exists? (str root "/" paths/root-filename))
      (seq (read-entity-files root "crew"))
      (seq (read-entity-files root "models"))
      (seq (read-entity-files root "providers"))))

(defn- schema-for [kind]
  (case kind
    :crew      schema/crew
    :defaults  schema/defaults
    :models    schema/model
    :providers schema/provider))

(defn- schema-error-entries [prefix result]
  (mapv (fn [[field message]]
          {:key   (if prefix
                    (str prefix "." (name field))
                    (name field))
           :value message})
        (cs/message-map result)))

(defn- companion-soul-error? [entity md-content]
  (and (present? (:soul entity))
       (present? md-content)))

(defn- top-level-warnings [data]
  (reduce (fn [acc key]
            (if (contains? (schema/schema-fields schema/root) key)
              acc
              (conj acc (warning (name key) "unknown key"))))
          []
          (keys data)))

(defn- load-root-config [root {:keys [substitute-env?] :as opts}]
  (let [overlay  (overlay-for opts paths/root-filename)
        path     (str root "/" paths/root-filename)]
    (cond
      overlay
      (let [{:keys [content relative]} overlay]
        (try
          (let [data            (read-edn-string content substitute-env?)
                root-result     (cs/conform schema/root data)
                defaults-result (when-let [defaults (:defaults data)]
                                  (cs/conform schema/defaults defaults))]
            {:data     data
             :errors   (vec (concat (when (cs/error? root-result) (schema-error-entries nil root-result))
                                    (when (and defaults-result (cs/error? defaults-result))
                                      (schema-error-entries "defaults" defaults-result))))
             :warnings (top-level-warnings data)
             :sources  [(source-path relative)]})
          (catch Exception _
            {:data nil :errors [{:key paths/root-filename :value "EDN syntax error"}] :warnings [] :sources []})))

      (fs/exists? path)
      (let [{:keys [data error]} (read-edn-file path substitute-env?)]
        (if error
          {:data nil :errors [{:key paths/root-filename :value error}] :warnings [] :sources []}
          (let [root-result     (cs/conform schema/root data)
                defaults-result (when-let [defaults (:defaults data)]
                                  (cs/conform schema/defaults defaults))]
            {:data     data
             :errors   (vec (concat (when (cs/error? root-result) (schema-error-entries nil root-result))
                                    (when (and defaults-result (cs/error? defaults-result))
                                      (schema-error-entries "defaults" defaults-result))))
             :warnings (top-level-warnings data)
             :sources  [(source-path paths/root-filename)]})))

      :else
      {:data nil :errors [] :warnings [] :sources []})))

(defn- merge-root-entity [result kind]
  (reduce (fn [acc [id entity]]
            (let [id        (->id id)
                  warnings  (collect-unknown-key-warnings [] (name kind) id entity (schema-for kind))
                  entity    (cs/conform (schema-for kind) entity)
                  explicit  (:id entity)]
              (-> acc
                  (update :warnings into warnings)
                  (cond-> (cs/error? entity)
                    (update :errors into (schema-error-entries (str (name kind) "." id) entity)))
                  (cond-> (and explicit (not= explicit id))
                    (assoc-error (str (name kind) "." id ".id") (str "must match filename (got \"" explicit "\")")))
                  (cond-> (not (cs/error? entity))
                    (assoc-in [:config kind id] (dissoc entity :id))))))
            result
            (get-in result [:root kind])))

(defn- load-entity-file [result root kind {:keys [content id overlay? path relative]} substitute-env?]
  (let [{:keys [data error]} (if overlay?
                               (try
                                 {:data (read-edn-string content substitute-env?)}
                                 (catch Exception _ {:error "EDN syntax error"}))
                               (read-edn-file path substitute-env?))
        md-content           (when (= kind :crew)
                                (let [md-path (str root "/" (paths/soul-relative id))]
                                  (when (fs/exists? md-path)
                                   (fs/slurp md-path))))]
    (cond
      error
      (assoc-error result relative error)

      (not (map? data))
      (assoc-error result relative "must contain a map")

      :else
        (let [warnings    (collect-unknown-key-warnings [] (name kind) id data (schema-for kind))
             entity      (cs/conform (schema-for kind) data)
             explicit-id (:id entity)
             result      (update result :warnings into warnings)
             result      (if (cs/error? entity)
                           (update result :errors into (schema-error-entries (str (name kind) "." id) entity))
                           result)
             result      (if (and explicit-id (not= explicit-id id))
                           (assoc-error result (str (name kind) "." id ".id") (str "must match filename (got \"" explicit-id "\")"))
                           result)
            result      (if (and (get-in (:config result) [kind id])
                                 (get-in (:root result) [kind id]))
                          (assoc-error result (str (name kind) "." id) (str "defined in both isaac.edn and " relative))
                          result)
             result      (if (companion-soul-error? entity md-content)
                           (assoc-error result (str (name kind) "." id ".soul") "must be set in .edn OR .md")
                           result)
             entity      (cond-> entity
                           (and (not (cs/error? entity)) (= kind :crew) (not (present? (:soul entity))) (present? md-content)) (assoc :soul md-content))]
         (if (or (some? (get-in (:root result) [kind id]))
                 (cs/error? entity))
           (update result :sources conj (source-path relative))
           (-> result
               (assoc-in [:config kind id] (dissoc entity :id))
              (update :sources conj (source-path relative))))))))

(defn- entity-files [root dir-name opts]
  (let [files   (read-entity-files root dir-name)
        overlay (when-let [relative (overlay-relative opts)]
                  (when (str/starts-with? relative (str dir-name "/"))
                    (let [name (last (str/split relative #"/"))]
                      {:id       (subs name 0 (- (count name) 4))
                       :relative relative
                       :content  (:overlay-content opts)
                       :overlay? true})))
        files   (if (and overlay (not-any? #(= (:relative overlay) (:relative %)) files))
                  (conj files overlay)
                  files)]
    (sort-by :relative files)))

(defn- semantic-errors [config]
  (let [crew      (:crew config)
        models    (:models config)
        providers (:providers config)
        defaults  (:defaults config)]
    (vec
      (concat
        (when-let [crew-id (:crew defaults)]
          (when-not (contains? crew crew-id)
            [{:key "defaults.crew"
              :value (str "references undefined crew \"" crew-id "\"")}]))
        (when-let [model-id (:model defaults)]
          (when-not (contains? models model-id)
            [{:key "defaults.model"
              :value (str "references undefined model \"" model-id "\"")}]))
        (mapcat (fn [[crew-id crew-cfg]]
                  (let [model-id (:model crew-cfg)]
                    (when (and model-id (not (contains? models model-id)))
                      [{:key   (str "crew." crew-id ".model")
                        :value (str "references undefined model \"" model-id "\"")}])) )
                crew)
        (mapcat (fn [[model-id model-cfg]]
                  (let [provider-id (:provider model-cfg)]
                    (when (and provider-id (not (contains? providers provider-id)))
                      [{:key   (str "models." model-id ".provider")
                        :value (str "references undefined provider \"" provider-id "\"")}])) )
                models)))))

(defn normalize-config [cfg]
  (let [crew-block     (or (:crew cfg) (:agents cfg) {})
        defaults       (or (:defaults cfg) (:defaults crew-block) {})
        old-crew-list  (or (:list crew-block) [])
        old-models     (or (:models crew-block) {})
        old-providers  (or (get-in cfg [:models :providers]) [])
        new-crew       (cond
                         (and (map? (:crew cfg))
                              (empty? (set/intersection #{:defaults :list :models} (set (keys (:crew cfg))))))
                         (into {} (map (fn [[id entity]] [(->id id) (normalize-crew entity)])) (:crew cfg))

                         (seq old-crew-list)
                         (into {} (map (fn [entity] [(->id (:id entity)) (normalize-crew entity)])) old-crew-list)

                         :else {})
        new-models     (cond
                         (and (map? (:models cfg))
                              (not (vector? (:models cfg)))
                              (not (:providers (:models cfg))))
                         (into {} (map (fn [[id entity]] [(->id id) (normalize-model entity)])) (:models cfg))

                         (seq old-models)
                         (into {} (map (fn [[id entity]] [(->id id) (normalize-model entity)])) old-models)

                         :else {})
        new-providers  (cond
                         (map? (:providers cfg))
                         (into {} (map (fn [[id entity]] [(->id id) entity])) (:providers cfg))

                         (seq old-providers)
                         (into {} (map (fn [entity] [(->id (or (:id entity) (:name entity))) (dissoc entity :name)])) old-providers)

                         :else {})]
    (cond-> {:defaults  (normalize-defaults defaults)
             :crew      new-crew
             :models    new-models
             :providers new-providers}
      (contains? cfg :channels) (assoc :channels (:channels cfg))
      (contains? cfg :comms)    (assoc :comms (:comms cfg))
      (contains? cfg :server)  (assoc :server (:server cfg))
      (contains? cfg :sessions) (assoc :sessions (:sessions cfg))
      (contains? cfg :gateway) (assoc :gateway (:gateway cfg))
      (contains? cfg :dev)     (assoc :dev (:dev cfg))
      (contains? cfg :acp)     (assoc :acp (:acp cfg))
      (contains? cfg :prefer-entity-files) (assoc :prefer-entity-files (:prefer-entity-files cfg)))))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Loading -----

(defn load-config-result
  [& [{:keys [home substitute-env? skip-entity-files? data-path-overlay]
       :or   {home (System/getProperty "user.home") substitute-env? true}
       :as   opts}]]
  (binding [*isaac-home* home]
    (let [root (paths/config-root home)
          opts (assoc opts :substitute-env? substitute-env?)]
      (if-not (config-files-present? root opts)
        {:config          {}
         :errors          [{:key "config" :value (missing-config-message home)}]
         :missing-config? true
         :warnings        []
         :sources         []}
        (let [{root-data :data root-errors :errors root-warnings :warnings root-sources :sources} (load-root-config root opts)
              base-config (normalize-config (or root-data {}))
              result      {:config   base-config
                            :errors   root-errors
                            :missing-config? false
                            :warnings root-warnings
                            :sources  root-sources
                            :root     (normalize-config (or root-data {}))}
              result      (reduce merge-root-entity result [:crew :models :providers])
              result      (cond-> result
                            (not skip-entity-files?)
                            (as-> r (reduce (fn [acc entity-file] (load-entity-file acc root :crew entity-file substitute-env?)) r (entity-files root "crew" opts))
                                  (reduce (fn [acc entity-file] (load-entity-file acc root :models entity-file substitute-env?)) r (entity-files root "models" opts))
                                  (reduce (fn [acc entity-file] (load-entity-file acc root :providers entity-file substitute-env?)) r (entity-files root "providers" opts))))
              config      (update (:config result) :defaults normalize-defaults)
              config      (if data-path-overlay
                            (assoc-in config (:path data-path-overlay) (:value data-path-overlay))
                            config)
              errors      (into (:errors result) (semantic-errors config))]
          {:config   config
           :errors   (vec (sort-by :key errors))
           :warnings (vec (sort-by :key (:warnings result)))
           :sources  (vec (sort (:sources result)))})))))

(defn load-config
  [& [opts]]
  (:config (apply load-config-result (when opts [opts]))))

;; endregion ^^^^^ Loading ^^^^^

;; region ----- Workspace -----

(defn resolve-workspace
  [crew-id & [{:keys [home] :or {home (System/getProperty "user.home")}}]]
  (let [crew-dir  (str home "/.isaac/crew/" crew-id)
        oc-dir    (str home "/.openclaw/workspace-" crew-id)
        isaac-dir (str home "/.isaac/workspace-" crew-id)]
    (cond
      (some? (fs/children crew-dir))  crew-dir
      (some? (fs/children oc-dir))    oc-dir
      (some? (fs/children isaac-dir)) isaac-dir
      :else                           nil)))

(defn read-workspace-file
  [crew-id filename & [{:as opts}]]
  (when-let [ws-dir (resolve-workspace crew-id opts)]
    (let [path (str ws-dir "/" filename)]
      (when (fs/exists? path)
        (fs/slurp path)))))

;; endregion ^^^^^ Workspace ^^^^^

;; region ----- Resolution -----

(defn resolve-provider [cfg provider-id]
  (get-in (normalize-config cfg) [:providers (->id provider-id)]))

(defn parse-model-ref [model-ref]
  (let [idx (str/index-of model-ref "/")]
    (when idx
      {:provider (subs model-ref 0 idx)
       :model    (subs model-ref (inc idx))})))

(defn resolve-crew [cfg crew-id]
  (let [cfg      (normalize-config cfg)
        crew-id  (->id crew-id)
        defaults (:defaults cfg)]
    (merge (when-let [model-id (:model defaults)] {:model model-id})
           (get-in cfg [:crew crew-id] {}))))

(defn resolve-crew-context [cfg crew-id & [{:keys [home] :as opts}]]
  (let [cfg            (normalize-config cfg)
        crew-id        (->id crew-id)
        crew-cfg       (resolve-crew cfg crew-id)
        model-id       (or (:model crew-cfg) (get-in cfg [:defaults :model]))
        model-cfg      (get-in cfg [:models model-id])
        provider-id    (:provider model-cfg)
        provider-cfg   (get-in cfg [:providers provider-id])]
    {:soul            (or (:soul crew-cfg)
                          (read-workspace-file crew-id "SOUL.md" opts)
                          "You are Isaac, a helpful AI assistant.")
     :model           (:model model-cfg)
     :provider        provider-id
     :context-window  (or (:context-window model-cfg)
                          (:context-window provider-cfg)
                          32768)
     :provider-config (or provider-cfg {})}))

(defn server-config [config]
  (let [config (normalize-config config)
        dev    (get config :dev)]
    {:port (or (get-in config [:server :port])
               (get-in config [:gateway :port])
               6674)
     :host (or (get-in config [:server :host])
               (get-in config [:gateway :host])
               "0.0.0.0")
     :dev  (cond
             (boolean? dev) dev
             (string? dev)  (contains? #{"1" "true" "yes" "on"} (str/lower-case dev))
             :else          false)}))

;; endregion ^^^^^ Resolution ^^^^^
