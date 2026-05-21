(ns isaac.charge
  (:require
    [clojure.string :as str]
    [isaac.config.loader :as config]))

(def charge-schema
  {:name   :charge
   :type   :map
   :schema {:session-key       {:type :string  :description "Session identifier"}
            :input             {:type :string  :description "User input string"}
            :comm              {:type :ignore  :description "Communication channel"}
            :crew              {:type :string  :description "Resolved crew/agent id"}
            :crew-members      {:type :ignore  :description "Full crew config map (all members)"}
            :models            {:type :ignore  :description "All configured models map"}
            :module-index      {:type :ignore  :description "Module index map"}
            :context-window    {:type :long    :description "Context window token limit"}
            :model             {:type :string  :description "Resolved model id"}
            :model-cfg         {:type :ignore  :description "Model configuration map"}
            :provider          {:type :ignore  :description "Resolved LLM provider Api instance"}
            :provider-cfg      {:type :ignore  :description "Provider configuration map"}
            :resolved-turn-ctx {:type :ignore  :description "Resolved session behavior context"}
            :soul              {:type :string  :description "System prompt"}
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
  ((requiring-resolve 'isaac.bridge.cancellation/cancelled?)
   (:session-key charge)))

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
  "Returns the session transcript (fetched lazily from the session store at run time)."
  [_charge]
  nil)

;; endregion ^^^^^ Accessors ^^^^^

;; region ----- Construction -----

(defn- ensure-provider [provider cfg]
  (cond
    (nil? provider)    nil
    (string? provider) (let [prov-cfg     (config/resolve-provider cfg provider)
                             enriched-cfg (merge (or prov-cfg {})
                                                 {:providers    (:providers cfg)
                                                  :module-index (:module-index cfg)})]
                         ((requiring-resolve 'isaac.drive.dispatch/make-provider) provider enriched-cfg))
    :else              provider))

(defn build
  "Build a charge from a request map.

   Reads from the global config snapshot and resolves the crew's full agent
   context (soul, model, model-cfg, provider, context-window). On resolution
   failure (unknown crew error or no model) returns a charge marked
   :charge/unresolved with a :charge/reason keyword."
  [{:keys [session-key input comm crew cfg home model model-ref model-override model-cfg
           provider provider-cfg context-window soul soul-prepend dispatch-error]}]
  (let [cfg*         (or cfg (config/snapshot) {})
        crew-id      (or crew (get-in cfg* [:defaults :crew]) "main")
        model-ref*   (or model-override model-ref model)
        known-crews  (or (:crew cfg*) {})
        default-crew (get-in cfg* [:defaults :crew])
        unknown?     (and (seq known-crews)
                          (not (or (= crew-id "main")
                                   (contains? known-crews crew-id)
                                   (= crew-id default-crew))))]
    (if (:error dispatch-error)
      {:charge/type      :charge
       :charge/unresolved true
       :charge/reason    (:error dispatch-error)
       :session-key      session-key
       :input            input
       :comm             comm
       :crew             crew-id
       :crew-members     known-crews
       :models           (:models cfg*)
       :module-index     (:module-index cfg*)}
      (if unknown?
        {:charge/type      :charge
         :charge/unresolved true
         :charge/reason    :unknown-crew
         :session-key      session-key
         :input            input
         :comm             comm
         :crew             crew-id
         :crew-members     known-crews
         :models           (:models cfg*)
         :module-index     (:module-index cfg*)}
      (let [ctx      ((requiring-resolve 'isaac.session.context/resolve-behavior)
                      session-key {:cfg cfg* :crew crew-id :home home :model model-ref*})
            eff-soul (or soul
                         (cond-> (:soul ctx)
                           soul-prepend (str "\n\n" soul-prepend)))
            model*   (or model (get-in ctx [:model-cfg :model]) (:model ctx))]
        (if (nil? model*)
          {:charge/type      :charge
           :charge/unresolved true
           :charge/reason    :no-model
           :session-key      session-key
           :input            input
           :comm             comm
           :crew             crew-id
           :crew-members     known-crews
           :models           (:models cfg*)
           :module-index     (:module-index cfg*)}
          {:charge/type       :charge
           :session-key       session-key
           :input             input
           :comm              comm
           :crew              crew-id
           :crew-members      known-crews
           :models            (:models cfg*)
           :module-index      (:module-index cfg*)
           :context-window    (or context-window (:context-window ctx))
           :model             model*
           :model-cfg         (or model-cfg (:model-cfg ctx))
           :provider          (ensure-provider (or provider (:provider ctx)) cfg*)
           :provider-cfg      (or provider-cfg (:provider-cfg ctx))
           :resolved-turn-ctx ctx
           :soul              eff-soul}))))))


;; endregion ^^^^^ Construction ^^^^^
