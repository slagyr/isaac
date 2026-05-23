(ns isaac.bridge.core
  (:require
    [clojure.string :as str]
    [isaac.bridge.status :as status]
    [isaac.charge :as charge]
    [isaac.comm :as comm]
    [isaac.config.loader :as config]
    [isaac.drive.turn :as turn]
    [isaac.logger :as log]
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
        session-store* (store/resolve-store request "bridge dispatch")
        cfg            (or (:cfg request) (config/snapshot) {})
        crew-id        (or (:crew request) (get-in cfg [:defaults :crew]) "main")
        crew-cfg       (get (:crew cfg) crew-id)
        resolved-cwd   (resolve-session-cwd (:cwd request) crew-cfg nil)]
    (when (and session-key
               (nil? (store/get-session session-store* session-key))
               (or (:origin request) resolved-cwd))
      (session-ctx/create-with-resolved-behavior!
        session-key {:crew          crew-id
                     :cwd           resolved-cwd
                     :home          (or (:home request) (:state-dir request))
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
    (let [session-store* (store/resolve-store c "bridge dispatch")
          session-key    (:session-key c)]
      (if (store/mark-in-flight! session-store* session-key)
        (try
          (route-charge! (assoc c :session-store session-store*))
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
      (let [request (merge (store/runtime-ctx) input)]
        (ensure-session! request)
        (dispatch-charge! (charge/build request)))))
  ([state-dir request]
    (dispatch! (assoc request :state-dir state-dir :home (or (:home request) state-dir)))))

;; endregion ^^^^^ Triage ^^^^^
