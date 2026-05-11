(ns isaac.bridge.core
  (:require
    [clojure.string :as str]
    [isaac.bridge.status :as status]
    [isaac.comm :as comm]
    [isaac.config.loader :as config]
    [isaac.session.store :as store]
    [isaac.session.store.file :as file-store]
    [isaac.slash.registry :as slash-registry]
    [isaac.system :as system]))

;; region ----- Helpers -----

(defn- session-store []
  (or (system/get :session-store)
      (file-store/create-store (system/get :state-dir))))

(defn- parse-command [input]
  (let [parts (str/split (str/trim input) #"\s+" 2)
        cmd   (subs (first parts) 1)]
    {:name cmd :args (second parts)}))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Slash Command Handlers -----

(defn- handle-slash [session-key input ctx]
  (let [{:keys [name]} (parse-command input)]
    (if-let [command (slash-registry/lookup name (:module-index ctx))]
      ((:handler command) session-key input ctx)
      {:type    :command
       :command :unknown
       :message (str "unknown command: /" name)})))

;; endregion ^^^^^ Slash Command Handlers ^^^^^

;; region ----- Turn Resolution -----

(defn- override-model-context [cfg ctx model-ref]
  (if-not model-ref
    ctx
    (let [alias-match  (or (get-in cfg [:models model-ref])
                           (get-in cfg [:models (keyword model-ref)]))
          parsed       (when-not alias-match (config/parse-model-ref model-ref))
          provider-id  (or (:provider alias-match) (:provider parsed))
          provider-cfg (when provider-id (config/resolve-provider cfg provider-id))]
      (if (or alias-match parsed)
        (assoc ctx
          :model          (or (:model alias-match) (:model parsed))
          :provider       (when provider-id
                            ((requiring-resolve 'isaac.drive.dispatch/make-provider)
                             provider-id (or provider-cfg {})))
          :context-window (or (:context-window alias-match)
                              (:context-window provider-cfg)
                              (:context-window ctx)
                              32768))
        ctx))))

(defn- ensure-provider-instance
  "Return p as-is if it is already an Api instance; if it is a provider-id
   string, instantiate it using the ambient config. Returns nil for nil input."
  [p cfg]
  (cond
    (nil? p)    nil
    (string? p) (let [prov-cfg (config/resolve-provider cfg p)]
                  ((requiring-resolve 'isaac.drive.dispatch/make-provider) p (or prov-cfg {})))
    :else       p))

(defn resolve-turn-opts
  "Resolve an inbound-turn-request into full turn opts.

   Reads crew/model/provider/soul/context-window from the ambient config snapshot
   (or from an explicit :cfg key in request, which takes precedence over snapshot).

   Optional pre-resolved override keys — :model, :provider, :context-window, :soul —
   win over the crew-resolved defaults."
  [{:keys [comm crew crew-id model-ref soul-prepend cfg session-key input
           model provider context-window soul]}]
  (let [cfg      (or cfg (config/snapshot) {})
        ctx      (config/resolve-crew-context cfg (or crew-id crew "main"))
        ctx      (if model-ref (override-model-context cfg ctx model-ref) ctx)
        eff-soul (or soul
                     (cond-> (:soul ctx)
                       soul-prepend (str "\n\n" soul-prepend)))]
    {:session-key    session-key
     :input          input
     :comm           comm
     :module-index   (:module-index cfg)
     :context-window (or context-window (:context-window ctx))
     :model          (or model (:model ctx))
     :provider       (ensure-provider-instance (or provider (:provider ctx)) cfg)
     :soul           eff-soul}))

;; endregion ^^^^^ Turn Resolution ^^^^^

;; region ----- Triage -----

(defn slash-command?
  "Returns true if input begins with a slash."
  [input]
  (and (string? input) (str/starts-with? input "/")))

(defn- slash-ctx [session-key opts]
  (let [session (store/get-session (session-store) session-key)
        cfg     (config/snapshot)]
    (assoc (select-keys opts [:model :provider :soul :context-window :boot-files])
           :models       (:models cfg)
           :module-index (:module-index cfg)
           :crew-members (:crew cfg)
           :crew         (or (:crew session) "main"))))

(defn dispatch
  "Triage input: dispatch slash commands or delegate to turn-fn.
   turn-fn is called as (turn-fn input opts) for non-command input.
   Returns {:type :command ...} or {:type :turn :result <turn-result>}."
  ([session-key input ctx turn-fn]
   (if (slash-command? input)
     (handle-slash session-key input ctx)
     {:type   :turn
      :input  input
      :result (when turn-fn (turn-fn input ctx))}))
  ([state-dir session-key input ctx turn-fn]
   (system/with-nested-system {:state-dir state-dir}
     (dispatch session-key input ctx turn-fn))))

(defn dispatch!
  "Comm-facing entry point. Slash commands are handled here; normal turns
   delegate to run-turn!. Bridge -> drive direction only.
   request must carry :session-key and :input.  All requests pass through
   resolve-turn-opts, which merges crew defaults with any pre-resolved override
   keys (:model, :provider, :context-window, :soul, :crew-members, :models)."
  ([{:keys [session-key input] :as request}]
   (let [opts (resolve-turn-opts request)]
     (if (slash-command? input)
       (let [ch     (:comm opts)
             ctx    (slash-ctx session-key opts)
             result (handle-slash session-key input ctx)
             output (if (contains? result :data)
                      (status/format-status (:data result))
                      (:message result))]
         (when ch
           (comm/on-text-chunk ch session-key output)
           (comm/on-turn-end ch session-key (assoc result :content output)))
         result)
       ((requiring-resolve 'isaac.drive.turn/run-turn!) session-key input opts))))
  ([state-dir request]
   (system/with-nested-system {:state-dir state-dir}
     (dispatch! request))))

;; endregion ^^^^^ Triage ^^^^^
