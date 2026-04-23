(ns isaac.session.context
  (:require
    [isaac.config.loader :as config]
    [isaac.fs :as fs]))

(defn- read-boot-files [cwd]
  (when cwd
    (let [path (str cwd "/AGENTS.md")]
      (when (fs/exists? path)
        (fs/slurp path)))))

(defn resolve-turn-context
  "Centralized per-turn context resolver. Returns {:soul :model :provider :provider-config :context-window}.
    Options:
      :cfg    - loaded config map (production path)
      :crew-members - injected crew map (compat/test path)
      :models - injected models map (test path)
      :cwd    - session cwd for AGENTS.md boot file lookup
      :home   - home directory for workspace SOUL.md lookup"
  [{:keys [cfg crew-members models cwd home] :as opts} crew-id]
  (let [crew-members (or crew-members (:agents opts))]
    (if cfg
      (assoc (config/resolve-crew-context cfg crew-id {:home home})
             :boot-files (read-boot-files cwd))
      (let [crew-cfg     (get crew-members crew-id)
            model-alias  (:model crew-cfg)
          model-cfg    (get models model-alias)]
        {:soul           (or (:soul crew-cfg)
                             (config/read-workspace-file crew-id "SOUL.md" {:home home})
                             "You are Isaac, a helpful AI assistant.")
         :boot-files     (read-boot-files cwd)
         :model          (:model model-cfg)
         :provider       (:provider model-cfg)
         :context-window (or (:context-window model-cfg) 32768)
         :provider-config {}}))))
