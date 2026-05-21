(ns isaac.bridge.core
  (:require
    [clojure.string :as str]
    [isaac.bridge.status :as status]
    [isaac.charge :as charge]
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
  "Resolves session cwd from the cascade: explicit override > crew > channel default.
   explicit-cwd: user-specified override (highest priority).
   crew-cfg: crew config map; may contain :cwd.
   channel-default: the channel's automatic fallback (lowest priority)."
  [explicit-cwd crew-cfg channel-default]
  (or explicit-cwd (:cwd crew-cfg) channel-default))

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
        known-crews    (or (:crew cfg) {})
        default-crew   (get-in cfg [:defaults :crew])
        crew-cfg       (get known-crews crew-id)
        request        (cond-> (assoc request :cfg cfg :crew-id crew-id)
                         (or model-override (:model session))
                         (assoc :model-ref (or model-override (:model session))))]
    (when (nil? session)
      (let [resolved-cwd (resolve-session-cwd (:cwd request) crew-cfg (:channel-cwd request))]
        (when (or (:origin request) resolved-cwd)
          ((requiring-resolve 'isaac.session.context/create-with-resolved-behavior!)
           session-key {:crew   crew-id
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

;; region ----- Triage -----

(defn slash-command?
  "Returns true if input begins with a slash."
  [input]
  (and (string? input) (str/starts-with? input "/")))

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

(defn- route-charge! [c]
  (let [ch          (:comm c)
        session-key (:session-key c)]
    (cond
      (charge/slash? c)
      (let [result (handle-slash session-key (:input c) c)
            output (if (contains? result :data)
                     (status/format-status (:data result))
                     (:message result))]
        (when ch
          (comm/on-text-chunk ch session-key output)
          (comm/on-turn-end ch session-key (assoc result :content output)))
        result)

      (charge/unresolved? c)
      (reject-turn session-key (:crew c) (:charge/reason c)
                   (case (:charge/reason c)
                     :unknown-crew (unknown-session-crew-message session-key (:crew c))
                     :no-model     (no-model-message (:crew c))
                     "resolution failed"))

      :else
      ((requiring-resolve 'isaac.drive.turn/run-turn!) c))))

(defn dispatch!
  "Comm-facing entry point. Accepts a charge (built via charge/build) or a
   legacy request map. Slash commands are handled here; normal turns delegate
   to run-turn!. Bridge -> drive direction only."
  ([input]
   (if (charge/charge? input)
     (route-charge! input)
     (let [pre (dispatch-request input)
           c   (charge/build (assoc pre :crew (:crew-id pre)))]
       (route-charge! c))))
  ([state-dir request]
   (system/with-nested-system {:state-dir state-dir}
     (dispatch! request))))

;; endregion ^^^^^ Triage ^^^^^
