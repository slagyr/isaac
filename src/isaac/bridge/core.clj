(ns isaac.bridge.core
  (:require
    [clojure.string :as str]
    [isaac.bridge.status :as status]
    [isaac.charge :as charge]
    [isaac.comm :as comm]
    [isaac.config.api :as config]
    [isaac.drive.turn :as turn]
    [isaac.logger :as log]
    [isaac.nexus :as nexus]
    [isaac.session.context :as session-ctx]
    [isaac.session.store :as store]
    [isaac.slash.builtin :as slash-builtin]
    [isaac.slash.registry :as slash-registry]))

;; region ----- Helpers -----

(defn resolve-session-cwd
  "Resolves session cwd from the cascade: explicit override > crew > channel default.
   explicit-cwd: user-specified override (highest priority).
   crew-cfg: crew config map; may contain :cwd.
   channel-default: the channel's automatic fallback (lowest priority)."
  [explicit-cwd crew-cfg channel-default]
  (or explicit-cwd (:cwd crew-cfg) channel-default))

(defn- unknown-session-crew-message [session-key crew-id origin]
  (let [kind (:kind origin)]
    (str "unknown crew on session " session-key ": " crew-id
         (cond
           (= :cli kind)                      "\npass --crew to override"
           (contains? #{:webhook :cron} kind) nil
           :else                              "\nsend /crew <name> to change crew"))))

(defn- no-model-message [crew-id]
  (str "no model configured for crew: " crew-id))

(defn- reject-turn [session-key crew-id reason message]
  (log/warn :drive/turn-rejected :session session-key :crew crew-id :reason reason)
  {:error reason :message message})

(defn- refuse-dispatch [session-key]
  (log/warn :dispatch/refused :reason :session-in-flight :session session-key)
  {:dispatched? false :reason :session-in-flight})

(defn- ensure-session! [request]
  (let [session-key    (:session-key request)
        session-store* (or (:session-store request) (nexus/get-in [:sessions :store]))
        cfg            (or (when (map? (:config request)) (:config request)) (config/snapshot "turn dispatch entry — falls back to ambient config when charge carries none") {})
        crew-id        (or (:crew request) (get-in cfg [:defaults :crew]) "main")
        crew-cfg       (get (:crew cfg) crew-id)
        resolved-cwd   (resolve-session-cwd (:cwd request) crew-cfg nil)]
    (when (and session-key
               (nil? (store/get-session session-store* session-key))
               (or (:origin request) resolved-cwd))
      (session-ctx/create-with-resolved-behavior!
        session-key {:crew          crew-id
                     :cwd           resolved-cwd
                     :origin        (:origin request)
                     :session-store session-store*}))))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Slash Command Handlers -----

(defn- handle-slash [session-key input ctx]
  (let [{:keys [name]} (slash-builtin/parse-command input)]
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
                     :unknown-crew (unknown-session-crew-message session-key (:crew c) (:origin c))
                     :no-model     (no-model-message (:crew c))
                     "resolution failed"))

      :else
      (turn/run-turn! c))))

(defn- dispatch-charge! [c]
  (if (or (charge/slash? c) (charge/unresolved? c) (nil? (:session-key c)))
    (route-charge! c)
    (let [session-store* (nexus/get-in [:sessions :store])
          session-key    (:session-key c)]
      (if (store/mark-in-flight! session-store* session-key)
        (try
          (route-charge! c)
          (finally
            (store/clear-in-flight! session-store* session-key)))
        (refuse-dispatch session-key)))))

(defn dispatch!
  "Comm-facing entry point. Accepts a charge (built via charge/build) or a
   request map (which gets passed through charge/build). Slash commands are
   handled here; normal turns delegate to run-turn!. Bridge -> drive only."
  ([input]
    (if (charge/charge? input)
      (dispatch-charge! input)
      (let [request (merge (nexus/necho) input)]
        (ensure-session! request)
        (dispatch-charge! (charge/build request)))))
  ([_state-dir request]
    ;; Two-arg form is a back-compat shim — state-dir now lives on the
    ;; config snapshot, which downstream readers consult directly.
    (dispatch! request)))

;; endregion ^^^^^ Triage ^^^^^
