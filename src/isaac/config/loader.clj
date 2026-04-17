(ns isaac.config.loader
  (:require
    [c3kit.apron.env :as c3env]
    [clojure.edn :as edn]
    [clojure.set :as set]
    [clojure.string :as str]
    [isaac.config.schema :as schema]
    [isaac.fs :as fs]))

;; region ----- Helpers -----

(defn env [name]
  (c3env/env name))

(defn- ->id [value]
  (cond
    (keyword? value) (name value)
    (string? value)  value
    (nil? value)     nil
    :else            (str value)))

(defn- config-root [home]
  (str home "/.isaac/config"))

(defn- warning [key value]
  {:key key :value value})

(defn- has-ext? [path ext]
  (str/ends-with? path ext))

(defn- relative-path [root path]
  (subs path (inc (count root))))

(defn- substitute-env [s]
  (str/replace s #"\$\{([^}]+)\}" (fn [[match var-name]] (or (env var-name) match))))

(defn- substitute-env-recursive [value]
  (cond
    (string? value)     (substitute-env value)
    (map? value)        (into {} (map (fn [[k v]] [k (substitute-env-recursive v)]) value))
    (sequential? value) (mapv substitute-env-recursive value)
    :else               value))

(defn- read-edn-file [path]
  (try
    {:data (-> path fs/slurp edn/read-string substitute-env-recursive)}
    (catch Exception _
      {:error "EDN syntax error"})))

(defn- present? [value]
  (and (some? value)
       (not (and (string? value) (str/blank? value)))))

(defn- assoc-error [result key value]
  (update result :errors conj {:key key :value value}))

(defn- assoc-warning [result key value]
  (update result :warnings conj {:key key :value value}))

(defn- normalize-defaults [defaults]
  (cond-> {}
    (contains? defaults :crew)  (assoc :crew (->id (:crew defaults)))
    (contains? defaults :model) (assoc :model (->id (:model defaults)))))

(defn- normalize-crew [crew]
  (cond-> {}
    (contains? crew :id)    (assoc :id (->id (:id crew)))
    (contains? crew :model) (assoc :model (->id (:model crew)))
    (contains? crew :soul)  (assoc :soul (:soul crew))
    (contains? crew :tools) (assoc :tools (update (:tools crew) :allow #(mapv ->id %)))))

(defn- normalize-model [model]
  (cond-> {}
    (contains? model :id)            (assoc :id (->id (:id model)))
    (contains? model :model)         (assoc :model (:model model))
    (contains? model :provider)      (assoc :provider (->id (:provider model)))
    (contains? model :contextWindow) (assoc :contextWindow (:contextWindow model))))

(defn- normalize-provider [provider]
  (cond-> (dissoc provider :name)
    (contains? provider :id) (assoc :id (->id (:id provider)))))

(defn- collect-unknown-key-warnings [warnings kind id entity allowed-keys]
  (reduce (fn [acc key]
            (if (contains? allowed-keys key)
              acc
              (conj acc (warning (str kind "." id "." (name key)) "unknown key"))))
          warnings
          (keys entity)))

(defn- read-entity-files [root dir-name]
  (let [dir (str root "/" dir-name)]
    (->> (or (fs/children dir) [])
         (filter #(has-ext? % ".edn"))
         sort
         (mapv (fn [name]
                 {:id       (subs name 0 (- (count name) 4))
                  :path     (str dir "/" name)
                  :relative (str dir-name "/" name)})))))

(defn- md-path [root dir-name id]
  (str root "/" dir-name "/" id ".md"))

(defn- root-config-file [root]
  (str root "/isaac.edn"))

(defn- config-files-present? [root]
  (or (fs/exists? (root-config-file root))
      (seq (read-entity-files root "crew"))
      (seq (read-entity-files root "models"))
      (seq (read-entity-files root "providers"))))

(defn- normalize-entity [kind entity]
  (case kind
    :crew      (normalize-crew entity)
    :models    (normalize-model entity)
    :providers (normalize-provider entity)
    entity))

(defn- allowed-keys-for [kind]
  (case kind
    :crew      schema/crew-keys
    :models    schema/model-keys
    :providers schema/provider-keys))

(defn- companion-soul-error? [entity md-content]
  (and (present? (:soul entity))
       (present? md-content)))

(defn- load-root-config [root]
  (let [path (root-config-file root)]
    (if (fs/exists? path)
      (let [{:keys [data error]} (read-edn-file path)]
        (if error
          {:data nil :errors [{:key "isaac.edn" :value error}]}
          {:data data :errors []}))
      {:data nil :errors []})))

(defn- merge-root-entity [result kind]
  (reduce (fn [acc [id entity]]
            (let [id        (->id id)
                  warnings  (collect-unknown-key-warnings [] (name kind) id entity (allowed-keys-for kind))
                  entity    (normalize-entity kind entity)
                  explicit  (:id entity)]
              (-> acc
                  (update :warnings into warnings)
                  (cond-> (and explicit (not= explicit id))
                    (assoc-error (str (name kind) "." id ".id") (str "must match filename (got \"" explicit "\")")))
                  (assoc-in [:config kind id] (dissoc entity :id)))))
            result
            (get-in result [:root kind])))

(defn- load-entity-file [result root kind {:keys [id path relative]}]
  (let [{:keys [data error]} (read-edn-file path)
        md-content           (when (= kind :crew)
                               (let [md-path (md-path root "crew" id)]
                                 (when (fs/exists? md-path)
                                   (fs/slurp md-path))))]
    (cond
      error
      (assoc-error result relative error)

      (not (map? data))
      (assoc-error result relative "must contain a map")

      :else
      (let [warnings    (collect-unknown-key-warnings [] (name kind) id data (allowed-keys-for kind))
            entity      (normalize-entity kind data)
            explicit-id (:id entity)
            result      (update result :warnings into warnings)
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
                          (and (= kind :crew) (not (present? (:soul entity))) (present? md-content)) (assoc :soul md-content))]
        (if (some? (get-in (:root result) [kind id]))
          result
          (assoc-in result [:config kind id] (dissoc entity :id)))))))

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
      (contains? cfg :server)  (assoc :server (:server cfg))
      (contains? cfg :gateway) (assoc :gateway (:gateway cfg))
      (contains? cfg :dev)     (assoc :dev (:dev cfg))
      (contains? cfg :acp)     (assoc :acp (:acp cfg)))))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Loading -----

(defn load-config-result
  [& [{:keys [home] :or {home (System/getProperty "user.home")}}]]
  (let [root       (config-root home)]
    (if-not (config-files-present? root)
      {:config   schema/default-config
       :errors   []
       :warnings []}
      (let [{root-data :data root-errors :errors} (load-root-config root)
            base-config (normalize-config (or root-data {}))
            result      {:config   base-config
                         :errors   root-errors
                         :warnings []
                         :root     base-config}
            result      (reduce merge-root-entity result [:crew :models :providers])
            result      (reduce (fn [acc entity-file] (load-entity-file acc root :crew entity-file)) result (read-entity-files root "crew"))
            result      (reduce (fn [acc entity-file] (load-entity-file acc root :models entity-file)) result (read-entity-files root "models"))
            result      (reduce (fn [acc entity-file] (load-entity-file acc root :providers entity-file)) result (read-entity-files root "providers"))
            config      (update (:config result) :defaults normalize-defaults)
            errors      (into (:errors result) (semantic-errors config))]
        {:config   config
         :errors   (vec (sort-by :key errors))
         :warnings (vec (sort-by :key (:warnings result)))}))))

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

(defn resolve-agent [cfg agent-id]
  (resolve-crew cfg agent-id))

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
     :context-window  (or (:contextWindow model-cfg)
                          (:contextWindow provider-cfg)
                          32768)
     :provider-config (or provider-cfg {})}))

(defn resolve-agent-context [cfg agent-id & [opts]]
  (resolve-crew-context cfg agent-id opts))

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
