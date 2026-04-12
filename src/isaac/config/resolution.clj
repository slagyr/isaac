(ns isaac.config.resolution
  (:require
    [c3kit.apron.env :as c3env]
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]))

;; region ----- Defaults -----

(def default-config
  {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
   :models {:providers [{:name    "ollama"
                         :baseUrl "http://localhost:11434"
                         :api     "ollama"}]}})

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

(defn- read-json-file [path]
  (let [f (io/file path)]
    (when (.exists f)
      (json/parse-string (slurp f) true))))

(defn load-config
  "Load configuration with OpenClaw fallback chain.
   Options: :home - home directory (default: user.home)"
  [& [{:keys [home] :or {home (System/getProperty "user.home")}}]]
  (let [openclaw-path (str home "/.openclaw/openclaw.json")
        isaac-path    (str home "/.isaac/isaac.json")]
    (substitute-env-recursive
      (or (read-json-file openclaw-path)
          (read-json-file isaac-path)
          default-config))))

;; endregion ^^^^^ Config File Resolution ^^^^^

;; region ----- Workspace Resolution -----

(defn resolve-workspace
  "Resolve workspace directory for an agent.
   Options: :home - home directory"
  [agent-id & [{:keys [home] :or {home (System/getProperty "user.home")}}]]
  (let [oc-dir    (str home "/.openclaw/workspace-" agent-id)
        isaac-dir (str home "/.isaac/workspace-" agent-id)]
    (cond
      (.isDirectory (io/file oc-dir))    oc-dir
      (.isDirectory (io/file isaac-dir)) isaac-dir
      :else                              nil)))

(defn read-workspace-file
  "Read a file from an agent's workspace. Returns content string or nil."
  [agent-id filename & [{:keys [home] :as opts}]]
  (when-let [ws-dir (resolve-workspace agent-id opts)]
    (let [f (io/file ws-dir filename)]
      (when (.exists f)
        (slurp f)))))

;; endregion ^^^^^ Workspace Resolution ^^^^^

;; region ----- Agent Resolution -----

(defn resolve-agent
  "Resolve agent config by merging defaults with agent-specific overrides."
  [config agent-id]
  (let [defaults (get-in config [:agents :defaults])
        agents   (get-in config [:agents :list])
        agent    (first (filter #(= agent-id (:id %)) agents))]
    (merge defaults agent)))

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

(defn resolve-agent-context
  "Resolve full agent config: soul, model, provider, context-window, provider-config.
   Returns nil for :model when no model is configured.
   Options: :home - home directory for workspace SOUL.md lookup"
  [cfg agent-id & [{:keys [home] :as opts}]]
  (let [agent-cfg     (resolve-agent cfg agent-id)
        model-ref     (or (:model agent-cfg) (get-in cfg [:agents :defaults :model]))
        agents-models (get-in cfg [:agents :models])
        alias-match   (get agents-models (keyword model-ref))
        parsed        (when (and model-ref (not alias-match)) (parse-model-ref model-ref))
        provider-name (or (:provider alias-match) (:provider parsed))
        provider      (when provider-name (resolve-provider cfg provider-name))]
    {:soul           (or (:soul agent-cfg)
                         (read-workspace-file agent-id "SOUL.md" opts)
                         "You are Isaac, a helpful AI assistant.")
     :model          (when model-ref
                       (or (:model alias-match) (:model parsed) model-ref))
     :provider       provider-name
     :context-window (or (:contextWindow alias-match) (:contextWindow provider) 32768)
     :provider-config (or provider {})}))

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
