(ns isaac.session.context
  (:require
    [isaac.config.resolution :as config]))

(defn resolve-turn-context
  "Centralized per-turn context resolver. Returns {:soul :model :provider :provider-config :context-window}.
   Options:
     :cfg    - loaded config map (production path)
     :agents - injected agents map (test path)
     :models - injected models map (test path)
     :home   - home directory for workspace SOUL.md lookup"
  [{:keys [cfg agents models home]} agent-id]
  (if cfg
    (config/resolve-agent-context cfg agent-id {:home home})
    (let [agent-cfg    (get agents agent-id)
          model-alias  (:model agent-cfg)
          model-cfg    (get models model-alias)]
      {:soul           (or (:soul agent-cfg)
                           (config/read-workspace-file agent-id "SOUL.md" {:home home})
                           "You are Isaac, a helpful AI assistant.")
       :model          (:model model-cfg)
       :provider       (:provider model-cfg)
       :context-window (or (:contextWindow model-cfg) 32768)
       :provider-config {}})))
