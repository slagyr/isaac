(ns isaac.config.resolution
  (:require
    [c3kit.apron.env :as c3env]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [isaac.fs :as fs]))

;; region ----- Defaults -----

(def default-config
  {:crew {:defaults {:model "ollama/qwen3-coder:30b"}}
   :models {:providers [{:name    "ollama"
                         :baseUrl "http://localhost:11434"
                         :api     "ollama"}]}})

(defn- crew-config [config]
  (or (:crew config) (:agents config) {}))

;; endregion ^^^^^ Defaults ^^^^^

;; region ----- Env Substitution -----

(defn env [var-name]
  (c3env/env var-name))

(defn- substitute-env [s]
  (str/replace s #"\$\{([^}]+)\}" (fn [[_ var-name]] (or (env var-name) ""))))

(defn- substitute-env-recursive [v]
  (cond
    (string? v)     (substitute-env v)
    (map? v)        (into {} (map (fn [[k val]] [k (substitute-env-recursive val)]) v))
    (sequential? v) (mapv substitute-env-recursive v)
    :else           v))

;; endregion ^^^^^ Env Substitution ^^^^^

;; region ----- Config File Resolution -----

(defn- read-edn-file [path]
  (when (fs/exists? path)
    (edn/read-string (fs/slurp path))))

(defn load-config
  "Load configuration from ~/.isaac/isaac.edn.
   Options: :home - home directory (default: user.home)"
  [& [{:keys [home] :or {home (System/getProperty "user.home")}}]]
  (let [path (str home "/.isaac/isaac.edn")]
    (substitute-env-recursive
      (or (read-edn-file path)
          default-config))))

;; endregion ^^^^^ Config File Resolution ^^^^^

;; region ----- Workspace Resolution -----

(defn resolve-workspace
  "Resolve workspace directory for a crew member.
   Options: :home - home directory"
  [crew-id & [{:keys [home] :or {home (System/getProperty "user.home")}}]]
  (let [crew-dir  (str home "/.isaac/crew/" crew-id)
        oc-dir    (str home "/.openclaw/workspace-" crew-id)
        isaac-dir (str home "/.isaac/workspace-" crew-id)]
    (cond
      (some? (fs/children crew-dir))  crew-dir
      (some? (fs/children oc-dir))    oc-dir
      (some? (fs/children isaac-dir)) isaac-dir
      :else                                     nil)))

(defn read-workspace-file
  "Read a file from a crew member's workspace. Returns content string or nil."
  [crew-id filename & [{:keys [home] :as opts}]]
  (when-let [ws-dir (resolve-workspace crew-id opts)]
    (let [path (str ws-dir "/" filename)]
      (when (fs/exists? path)
        (fs/slurp path)))))

;; endregion ^^^^^ Workspace Resolution ^^^^^

;; region ----- Agent Resolution -----

(defn resolve-crew
  "Resolve crew config by merging defaults with crew-specific overrides."
  [config crew-id]
  (let [crew      (crew-config config)
        defaults  (:defaults crew)
        crew-list (:list crew)
        crew-row  (first (filter #(= crew-id (:id %)) crew-list))]
    (merge defaults crew-row)))

(defn resolve-agent [config agent-id]
  (resolve-crew config agent-id))

;; endregion ^^^^^ Agent Resolution ^^^^^

;; region ----- Model Resolution -----

(defn parse-model-ref
  "Parse a provider/model reference string."
  [model-ref]
  (let [idx (str/index-of model-ref "/")]
    (when idx
      {:provider (subs model-ref 0 idx)
       :model    (subs model-ref (inc idx))})))

(defn resolve-provider
  "Resolve provider config by name."
  [config provider-name]
  (let [providers (get-in config [:models :providers])]
    (first (filter #(= provider-name (:name %)) providers))))

;; endregion ^^^^^ Model Resolution ^^^^^

;; region ----- Agent Context Resolution -----

(defn resolve-crew-context
  "Resolve full crew config: soul, model, provider, context-window, provider-config.
   Returns nil for :model when no model is configured.
   Options: :home - home directory for workspace SOUL.md lookup"
  [cfg crew-id & [{:keys [home] :as opts}]]
  (let [crew         (crew-config cfg)
        crew-cfg     (resolve-crew cfg crew-id)
        model-ref    (or (:model crew-cfg) (get-in crew [:defaults :model]))
        crew-models  (:models crew)
        alias-match  (get crew-models (keyword model-ref))
        parsed        (when (and model-ref (not alias-match)) (parse-model-ref model-ref))
        provider-name (or (:provider alias-match) (:provider parsed))
        provider      (when provider-name (resolve-provider cfg provider-name))]
    {:soul           (or (:soul crew-cfg)
                         (read-workspace-file crew-id "SOUL.md" opts)
                          "You are Isaac, a helpful AI assistant.")
      :model          (when model-ref
                        (or (:model alias-match) (:model parsed) model-ref))
      :provider       provider-name
      :context-window (or (:contextWindow alias-match) (:contextWindow provider) 32768)
      :provider-config (or provider {})}))

(defn resolve-agent-context [cfg agent-id & [opts]]
  (resolve-crew-context cfg agent-id opts))

;; endregion ^^^^^ Agent Context Resolution ^^^^^

;; region ----- Server Config -----

(defn server-config
  "Resolve server startup config, aliasing gateway.* as a fallback for server.*"
  [config]
  (let [dev (get config :dev)]
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

;; endregion ^^^^^ Server Config ^^^^^
