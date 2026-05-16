(ns isaac.bridge.core
  (:require
    [clojure.string :as str]
    [isaac.bridge.status :as status]
    [isaac.comm :as comm]
    [isaac.config.loader :as config]
    [isaac.logger :as log]
    [isaac.session.store :as store]
    [isaac.session.store.file :as file-store]
    [isaac.slash.registry :as slash-registry]
    [isaac.system :as system]))

;; region ----- Helpers -----

(defn- session-store []
  (or (system/get :session-store)
      (file-store/create-store (system/get :state-dir))))

(defn resolve-session-cwd
  "Resolves session cwd from the cascade: explicit request override > crew > nil (channel default).
   crew-cfg is the crew's config map; may contain :cwd."
  [request-cwd crew-cfg]
  (or request-cwd (:cwd crew-cfg)))

(defn- parse-command [input]
  (let [parts (str/split (str/trim input) #"\s+" 2)
        cmd   (subs (first parts) 1)]
    {:name cmd :args (second parts)}))

(defn- unknown-session-crew-message [session-key crew-id]
  (str "unknown crew on session " session-key ": " crew-id "\n"
       "pass --crew to override"))

(defn- no-model-message [crew-id]
  (str "no model configured for crew: " crew-id))

(defn- reject-turn [session-key crew-id reason message]
  (log/warn :drive/turn-rejected :session session-key :crew crew-id :reason reason)
  {:error reason :message message})

(defn- dispatch-request [request]
  (let [cfg            (or (:cfg request) (config/snapshot) {})
        session-key    (:session-key request)
        session        (store/get-session (session-store) session-key)
        crew-override  (or (:crew-override request) (:crew-id request) (:crew request))
        model-override (or (:model-override request) (:model-ref request))
        crew-id        (or crew-override
                           (:crew session)
                           (get-in cfg [:defaults :crew])
                           "main")
        norm-cfg       (config/normalize-config cfg)
        known-crews    (or (:crew norm-cfg) {})
        default-crew   (get-in norm-cfg [:defaults :crew])
        crew-cfg       (get known-crews crew-id)
        request        (cond-> (assoc request :cfg cfg :crew-id crew-id)
                         (or model-override (:model session))
                         (assoc :model-ref (or model-override (:model session))))]
    (when (nil? session)
      (let [resolved-cwd (resolve-session-cwd (:cwd request) crew-cfg)]
        (when (or (:origin request) resolved-cwd)
          (store/open-session! (session-store) session-key {:crew   crew-id
                                                            :cwd    resolved-cwd
                                                            :origin (:origin request)}))))
    (if (and (nil? crew-override)
             (or (:crew session) (:agent session))
             (seq known-crews)
             (not (or (= crew-id "main")
                      (contains? known-crews crew-id)
                      (= crew-id default-crew))))
      (assoc request :dispatch-error {:error   :unknown-crew
                                      :message (unknown-session-crew-message session-key crew-id)})
      request)))

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

(defn- ensure-provider-instance
  "Return p as-is if it is already an Api instance; if it is a provider-id
   string, instantiate it using the ambient config. Returns nil for nil input."
  [p cfg]
  (cond
    (nil? p)    nil
    (string? p) (let [prov-cfg     (config/resolve-provider cfg p)
                      enriched-cfg (merge (or prov-cfg {})
                                          {:providers    (:providers cfg)
                                           :module-index (:module-index cfg)})]
                  ((requiring-resolve 'isaac.drive.dispatch/make-provider) p enriched-cfg))
    :else       p))

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
   request must carry :session-key and :input; adapters may also pass
   :crew-override, :model-override, :origin, :cwd, :home, and :cfg."
  ([{:keys [session-key input] :as request}]
   (let [request               (dispatch-request request)
         {:keys [comm crew crew-id model-ref soul-prepend cfg home
                 model model-cfg provider provider-cfg context-window soul]} request
         ctx                   (config/resolve-crew-context cfg (or crew-id crew "main")
                                                            (cond-> {}
                                                              model-ref (assoc :model-override model-ref)
                                                              home      (assoc :home home)))
         eff-soul              (or soul
                                  (cond-> (:soul ctx)
                                    soul-prepend (str "\n\n" soul-prepend)))
         opts                  {:session-key    session-key
                                :input          input
                                :comm           comm
                                :crew           (or crew-id crew "main")
                                :module-index   (:module-index cfg)
                                :context-window (or context-window (:context-window ctx))
                                :model          (or model (:model ctx))
                                :model-cfg      model-cfg
                                :provider       (ensure-provider-instance (or provider (:provider ctx)) cfg)
                                :provider-cfg   provider-cfg
                                :soul           eff-soul}]
     (if-let [error (:dispatch-error request)]
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
         (reject-turn session-key (:crew opts) (:error error) (:message error)))
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
         (if (nil? (:model opts))
           (reject-turn session-key (:crew opts) :no-model (no-model-message (:crew opts)))
           ((requiring-resolve 'isaac.drive.turn/run-turn!) session-key input opts))))))
  ([state-dir request]
   (system/with-nested-system {:state-dir state-dir}
     (dispatch! request))))

;; endregion ^^^^^ Triage ^^^^^
