(ns isaac.charge
  (:require
    [clojure.string :as str]
    [isaac.bridge.cancellation :as cancellation]
    [isaac.config.loader :as config]
    [isaac.drive.dispatch :as drive-dispatch]
    [isaac.session.context :as session-ctx]
    [isaac.session.store :as store]))

(def charge-schema
  {:name   :charge
   :type   :map
   :schema {:session-key       {:type :string  :description "Session identifier"}
            :input             {:type :string  :description "User input string"}
            :comm              {:type :ignore  :description "Communication channel"}
            :state-dir         {:type :string  :description "Resolved Isaac state directory"}
            :session-store     {:type :ignore  :description "Session store instance for this charge"}
            :crew              {:type :string  :description "Resolved crew/agent id"}
            :crew-members      {:type :ignore  :description "Full crew config map (all members)"}
            :models            {:type :ignore  :description "All configured models map"}
            :module-index      {:type :ignore  :description "Module index map"}
            :context-window    {:type :long    :description "Context window token limit"}
            :model             {:type :string  :description "Resolved model id"}
            :model-cfg         {:type :ignore  :description "Model configuration map"}
            :provider          {:type :ignore  :description "Resolved LLM provider Api instance"}
            :provider-cfg      {:type :ignore  :description "Provider configuration map"}
            :crew-cfg          {:type :ignore  :description "Resolved crew configuration map"}
            :compaction        {:type :ignore  :description "Resolved compaction policy map"}
            :context-mode      {:type :keyword :description "Compaction/prompt-building mode (:full or :reset)"}
            :effort            {:type :long    :description "Resolved per-turn effort budget"}
            :cwd               {:type :string  :description "Session working directory"}
            :soul              {:type :string  :description "System prompt"}
            :origin            {:type :ignore  :description "Inbound origin metadata"}
            :charge/type       {:type :keyword :description "Charge type marker (:charge)"}
            :charge/unresolved {:type :boolean :description "True when crew/model could not be resolved"}
            :charge/reason     {:type :keyword :description "Reason for unresolved charge"}}})

;; region ----- Predicates -----

(defn charge? [x]
  (= :charge (:charge/type x)))

(defn slash?
  "True when the charge carries a slash-command input."
  [charge]
  (and (string? (:input charge))
       (str/starts-with? (:input charge) "/")))

(defn unresolved?
  "True when the charge could not be fully resolved (unknown crew, no model, etc.)."
  [charge]
  (true? (:charge/unresolved charge)))

(defn cancelled?
  "True when the session has been cancelled via the bridge cancellation registry."
  [charge]
  (cancellation/cancelled? (:session-key charge)))

;; endregion ^^^^^ Predicates ^^^^^

;; region ----- Accessors -----

(defn channel
  "Returns the comm channel for the charge."
  [charge]
  (:comm charge))

(defn agent
  "Returns the resolved crew/agent id."
  [charge]
  (:crew charge))

(defn transcript
  "Returns the active session transcript from the session store."
  [charge]
  (when-let [ss (:session-store charge)]
    (store/active-transcript ss (:session-key charge))))

;; endregion ^^^^^ Accessors ^^^^^

;; region ----- Construction -----

(defn- ensure-provider [provider cfg]
  (cond
    (nil? provider)    nil
    (string? provider) (let [prov-cfg     (config/resolve-provider cfg provider)
                             enriched-cfg (merge (or prov-cfg {})
                                                 {:providers    (:providers cfg)
                                                  :module-index (:module-index cfg)})]
                         (drive-dispatch/make-provider provider enriched-cfg))
    :else              provider))

(defn- unresolved-charge [base reason]
  (assoc base
    :charge/type       :charge
    :charge/unresolved true
    :charge/reason     reason))

(defn build
  "Build a charge from a request map.

   Reads from the global config snapshot and resolves the crew's full agent
   context (soul, model, model-cfg, provider, context-window, compaction,
   context-mode, effort). On resolution failure (unknown crew error or no
   model) returns a charge marked :charge/unresolved with a :charge/reason
   keyword."
  [{:keys [session-key input comm crew cfg home state-dir session-store model model-ref model-override model-cfg
           provider provider-cfg context-window soul soul-prepend origin dispatch-error]}]
  (let [cfg*         (or cfg (config/snapshot) {})
        home*        (or home state-dir)
        crew-id      (or crew (get-in cfg* [:defaults :crew]) "main")
        model-ref*   (or model-override model-ref model)
        known-crews  (or (:crew cfg*) {})
        default-crew (get-in cfg* [:defaults :crew])
        unknown?     (and (seq known-crews)
                          (not (or (= crew-id "main")
                                   (contains? known-crews crew-id)
                                   (= crew-id default-crew))))]
    (if (:error dispatch-error)
      (unresolved-charge {:session-key   session-key
                          :input         input
                          :comm          comm
                          :state-dir     state-dir
                          :session-store session-store
                          :crew          crew-id
                          :crew-members  known-crews
                          :models        (:models cfg*)
                          :module-index  (:module-index cfg*)
                          :origin        origin}
                         (:error dispatch-error))
      (if unknown?
        (unresolved-charge {:session-key   session-key
                            :input         input
                            :comm          comm
                            :state-dir     state-dir
                            :session-store session-store
                            :crew          crew-id
                            :crew-members  known-crews
                            :models        (:models cfg*)
                            :module-index  (:module-index cfg*)
                            :origin        origin}
                           :unknown-crew)
        (let [ctx      (session-ctx/resolve-behavior
                         session-key
                         {:cfg cfg* :crew crew-id :state-dir state-dir
                          :home home* :model model-ref* :session-store session-store})
              eff-soul (or soul
                           (cond-> (:soul ctx)
                             soul-prepend (str "\n\n" soul-prepend)))
              model*   (or model (get-in ctx [:model-cfg :model]) (:model ctx))
              base     {:session-key   session-key
                        :input         input
                        :comm          comm
                        :state-dir     state-dir
                        :session-store session-store
                        :crew          crew-id
                        :crew-members  known-crews
                        :models        (:models cfg*)
                        :module-index  (:module-index cfg*)
                        :origin        origin}]
          (if (nil? model*)
            (unresolved-charge base :no-model)
            {:charge/type    :charge
             :session-key    session-key
             :input          input
             :comm           comm
             :state-dir      state-dir
             :session-store  session-store
             :crew           crew-id
             :crew-members   known-crews
             :crew-cfg       (:crew-cfg ctx)
             :models         (:models cfg*)
             :module-index   (:module-index cfg*)
             :context-window (or context-window (:context-window ctx))
             :context-mode   (:context-mode ctx)
             :compaction     (:compaction ctx)
             :effort         (:effort ctx)
             :cwd            (:cwd ctx)
             :model          model*
             :model-cfg      (or model-cfg (:model-cfg ctx))
             :provider       (ensure-provider (or provider (:provider ctx)) cfg*)
             :provider-cfg   (or provider-cfg (:provider-cfg ctx))
             :soul           eff-soul
             :origin         origin}))))))

;; endregion ^^^^^ Construction ^^^^^
