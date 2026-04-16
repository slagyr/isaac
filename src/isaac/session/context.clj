(ns isaac.session.context
  (:require
    [isaac.config.resolution :as config]
    [isaac.fs :as fs]))

(defn- read-boot-files [cwd]
  (when cwd
    (let [path (str cwd "/AGENTS.md")]
      (when (fs/file-exists? fs/*fs* path)
        (fs/read-file fs/*fs* path)))))

(defn resolve-turn-context
  "Centralized per-turn context resolver. Returns {:soul :model :provider :provider-config :context-window}.
   Options:
     :cfg    - loaded config map (production path)
     :agents - injected crew map (compat/test path)
     :models - injected models map (test path)
     :cwd    - session cwd for AGENTS.md boot file lookup
     :home   - home directory for workspace SOUL.md lookup"
  [{:keys [cfg agents models cwd home]} agent-id]
  (if cfg
    (assoc (config/resolve-crew-context cfg agent-id {:home home})
           :boot-files (read-boot-files cwd))
    (let [agent-cfg    (get agents agent-id)
          model-alias  (:model agent-cfg)
          model-cfg    (get models model-alias)]
      {:soul           (or (:soul agent-cfg)
                           (config/read-workspace-file agent-id "SOUL.md" {:home home})
                           "You are Isaac, a helpful AI assistant.")
       :boot-files     (read-boot-files cwd)
       :model          (:model model-cfg)
       :provider       (:provider model-cfg)
       :context-window (or (:contextWindow model-cfg) 32768)
       :provider-config {}})))
