(ns isaac.session.context
  (:require
    [c3kit.apron.schema :as schema]
    [isaac.config.loader :as config]
    [isaac.effort :as effort]
    [isaac.fs :as fs]
    [isaac.logger :as log]
    [isaac.session.compaction-schema :as compaction-schema]
    [isaac.session.store :as store]
    [isaac.session.store.file :as file-store]
    [isaac.system :as system]))

(def LARGE_TURN_TOKENS 40000)
(def LARGE_FRONTMATTER_TOKENS 10000)

(def default-context-mode :full)

(defn read-boot-files [cwd]
  (when cwd
    (let [path (str cwd "/AGENTS.md")]
      (when (fs/exists? path)
        (fs/slurp path)))))

(defn default-threshold [window]
  (max (- window (+ LARGE_TURN_TOKENS LARGE_FRONTMATTER_TOKENS))
       (int (* 0.8 window))))

(defn default-head [window]
  (int (* 0.3 window)))

(defn- session-store []
  (or (system/get :session-store)
      (file-store/create-store (system/get :state-dir))))

(defn- effective-config [state-dir]
  (or (config/snapshot)
      (when state-dir
        (config/load-config {:home state-dir}))
      {}))

(defn- default-cwd [state-dir crew-id]
  (let [root (if (.endsWith state-dir "/.isaac") state-dir (str state-dir "/.isaac"))]
    (str root "/crew/" crew-id)))

(defn- merged-session [session overrides]
  (merge session (select-keys overrides [:compaction :context-mode :crew :cwd :effort :history-retention :model :provider])))

(declare resolve-compaction-config)

(defn- resolve-behavior* [cfg state-dir session-entry overrides]
  (let [session-entry  (merged-session session-entry overrides)
        crew-id        (or (:crew session-entry)
                           (:agent session-entry)
                           (get-in cfg [:defaults :crew])
                           "main")
        crew-cfg       (config/resolve-crew cfg crew-id)
        model-override (:model session-entry)
        model-ref      (or model-override
                           (:model crew-cfg)
                           (get-in cfg [:defaults :model]))
        ctx            (config/resolve-crew-context cfg crew-id
                                                    (cond-> {:home state-dir}
                                                      model-override (assoc :model-override model-override)))
        context-window (or (:context-window overrides)
                           (:context-window ctx)
                           32768)]
    {:compaction        (resolve-compaction-config cfg session-entry ctx context-window)
     :context-mode      (or (:context-mode session-entry)
                            (get-in ctx [:crew-cfg :context-mode])
                            (get-in cfg [:defaults :context-mode])
                            default-context-mode)
     :context-window    context-window
     :crew              crew-id
     :crew-cfg          (:crew-cfg ctx)
     :cwd               (or (:cwd session-entry)
                            (when state-dir (default-cwd state-dir crew-id)))
     :effort            (effort/resolve-effort session-entry
                                               (or (:crew-cfg ctx) {})
                                               (or (:model-cfg ctx) {})
                                               (or (config/resolve-provider cfg (get-in ctx [:model-cfg :provider])) {})
                                               (or (:defaults cfg) {}))
     :history-retention (or (:history-retention session-entry)
                            (config/resolve-history-retention cfg crew-id nil))
     :model             (or model-ref
                            (:model ctx))
     :model-cfg         (:model-cfg ctx)
     :provider          (:provider ctx)
     :provider-cfg      (or (config/resolve-provider cfg (get-in ctx [:model-cfg :provider])) {})
     :soul              (:soul ctx)}))

(defn resolve-compaction-config
  [cfg session-entry ctx context-window]
  (let [provider-id  (or (get-in ctx [:model-cfg :provider])
                         (get-in session-entry [:provider]))
        provider-cfg (or (config/resolve-provider cfg provider-id) {})
        defaults     {:async?    false
                      :strategy  :rubberband
                      :head      (default-head context-window)
                      :threshold (default-threshold context-window)}
        override     (or (:compaction session-entry)
                         (get-in ctx [:crew-cfg :compaction])
                         (get-in ctx [:model-cfg :compaction])
                         (:compaction provider-cfg)
                         (get-in cfg [:defaults :compaction])
                         {})
        raw          (merge defaults override)]
    (schema/coerce! compaction-schema/config-schema raw)))

(defn resolve-behavior
  ([session-key]
   (resolve-behavior session-key {}))
  ([session-key overrides]
   (let [state-dir      (or (:home overrides) (system/get :state-dir))
         cfg            (config/normalize-config (or (:cfg overrides)
                                                     (effective-config state-dir)))
         session-entry  (or (store/get-session (session-store) session-key) {})
         behavior       (resolve-behavior* cfg state-dir session-entry overrides)]
     (log/debug :session/behavior-resolved
                :session session-key
                :crew (:crew behavior)
                :model (:model behavior)
                :context-mode (:context-mode behavior)
                :history-retention (:history-retention behavior)
                :cwd (:cwd behavior))
     behavior)))

(defn create-with-resolved-behavior!
  [session-key opts]
  (let [state-dir  (or (:home opts) (system/get :state-dir))
        cfg        (config/normalize-config (or (:cfg opts)
                                                (effective-config state-dir)))
        behavior   (resolve-behavior* cfg state-dir {} opts)
        store      (session-store)
        entry      (store/open-session! store session-key {:channel           (:channel opts)
                                                           :chat-type         (or (:chat-type opts) (:chatType opts))
                                                           :crew              (:crew behavior)
                                                           :cwd               (:cwd behavior)
                                                           :history-retention (:history-retention behavior)
                                                           :origin            (:origin opts)})
        updates    (cond-> {}
                     (contains? opts :compaction)   (assoc :compaction (:compaction opts))
                     (contains? opts :context-mode) (assoc :context-mode (:context-mode opts))
                     (contains? opts :effort)       (assoc :effort (:effort opts))
                     (contains? opts :model)        (assoc :model (:model opts))
                     (contains? opts :provider)     (assoc :provider (:provider opts)))]
    (if (seq updates)
      (store/update-session! store (:id entry) updates)
      entry)))
