(ns isaac.bridge.core
  (:require
    [clojure.string :as str]
    [isaac.bridge.status :as status]
    [isaac.comm :as comm]
    [isaac.config.loader :as config]
    [isaac.fs :as fs]
    [isaac.home :as home]
    [isaac.session.logging :as logging]
    [isaac.session.store :as store]
    [isaac.session.store.file :as file-store]))

;; region ----- Helpers -----

(defn- session-store [state-dir]
  (file-store/create-store state-dir))

(defn- parse-command [input]
  (let [parts (str/split (str/trim input) #"\s+" 2)
        cmd   (subs (first parts) 1)]
    {:name cmd :args (second parts)}))

(defn- find-alias [models model provider]
  (some (fn [[alias cfg]]
          (when (and (= model (:model cfg)) (= provider (:provider cfg)))
            (if (keyword? alias) (name alias) (str alias))))
        models))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Slash Command Handlers -----

(defn- handle-model [state-dir session-key input ctx]
  (let [{:keys [args]} (parse-command input)
        models         (:models ctx)]
    (if (str/blank? args)
      (let [model    (:model ctx)
            provider (status/ctx-provider-name ctx)
            alias    (or (find-alias models model provider) model)]
        {:type    :command
         :command :model
         :message (str alias " (" provider "/" model ") is the current model")})
        (if-let [model-cfg (or (get models args)
                               (get models (keyword args)))]
          (do
          (store/update-session! (session-store state-dir) session-key {:model    (:model model-cfg)
                                                                        :provider (:provider model-cfg)})
           {:type    :command
            :command :model
            :message (str "switched model to " args " (" (:provider model-cfg) "/" (:model model-cfg) ")")})
        {:type    :command
         :command :unknown
         :message (str "unknown model: " args)}))))

(defn- handle-crew [state-dir session-key input ctx]
  (let [{:keys [args]} (parse-command input)
        current-crew (or (:crew ctx) "main")
        crew-members (or (:crew-members ctx) {})]
    (if (str/blank? args)
      {:type    :command
       :command :crew
         :message (str current-crew " is the current crew member")}
         (if (contains? crew-members args)
           (do
            (store/update-session! (session-store state-dir) session-key {:crew     args
                                                                          :model    nil
                                                                          :provider nil})
             (logging/log-crew-changed! session-key current-crew args)
             {:type    :command
              :command :crew
             :message (str "switched crew to " args)})
         {:type    :command
          :command :unknown
         :message (str "unknown crew: " args)}))))

(defn- resolve-cwd-path [state-dir path]
  (cond
    (str/starts-with? path "/") path
    (str/starts-with? path "~/") (str (home/user-home) (subs path 1))
    :else (str state-dir "/" path)))

(defn- handle-cwd [state-dir session-key input _ctx]
  (let [{:keys [args]} (parse-command input)]
    (if (str/blank? args)
      (let [cwd (:cwd (store/get-session (session-store state-dir) session-key))]
         {:type    :command
          :command :cwd
          :message (str "current directory: " (or cwd "(not set)"))})
      (let [resolved (resolve-cwd-path state-dir args)]
        (if (fs/dir? resolved)
          (do
            (store/update-session! (session-store state-dir) session-key {:cwd resolved})
            {:type    :command
             :command :cwd
             :message (str "working directory set to " resolved)})
          {:type    :command
           :command :unknown
           :message (str "no such directory: " args)})))))

(defn- handle-slash [state-dir session-key input ctx]
  (let [{:keys [name]} (parse-command input)]
    (case name
      "status"
      {:type    :command
       :command :status
       :data    (status/status-data state-dir session-key ctx)}

      "crew"
      (handle-crew state-dir session-key input ctx)

      "model"
      (handle-model state-dir session-key input ctx)

      "cwd"
      (handle-cwd state-dir session-key input ctx)

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
  (let [cfg          (or cfg (config/snapshot) {})
        ctx          (config/resolve-crew-context cfg (or crew-id crew "main"))
        ctx          (if model-ref (override-model-context cfg ctx model-ref) ctx)
        eff-soul     (or soul
                         (cond-> (:soul ctx)
                           soul-prepend (str "\n\n" soul-prepend)))]
    {:session-key    session-key
     :input          input
     :comm           comm
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

(defn- slash-ctx [state-dir session-key opts]
  (let [session (store/get-session (session-store state-dir) session-key)
         cfg     (config/snapshot)]
    (assoc (select-keys opts [:model :provider :soul :context-window :boot-files])
           :models       (:models cfg)
           :crew-members (:crew cfg)
           :crew         (or (:crew session) "main"))))

(defn dispatch
  "Triage input: dispatch slash commands or delegate to turn-fn.
   turn-fn is called as (turn-fn input opts) for non-command input.
   Returns {:type :command ...} or {:type :turn :result <turn-result>}."
  [state-dir session-key input ctx turn-fn]
  (if (slash-command? input)
    (handle-slash state-dir session-key input ctx)
    {:type   :turn
     :input  input
     :result (when turn-fn (turn-fn input ctx))}))

(defn dispatch!
  "Comm-facing entry point. Slash commands are handled here; normal turns
   delegate to run-turn!. Bridge → drive direction only.
   request must carry :session-key and :input.  All requests pass through
   resolve-turn-opts, which merges crew defaults with any pre-resolved override
   keys (:model, :provider, :context-window, :soul, :crew-members, :models)."
  [state-dir {:keys [session-key input] :as request}]
  (let [opts (resolve-turn-opts request)]
    (if (slash-command? input)
      (let [ch      (:comm opts)
            ctx     (slash-ctx state-dir session-key opts)
            result  (handle-slash state-dir session-key input ctx)
            output  (if (= :status (:command result))
                      (status/format-status (:data result))
                      (:message result))]
        (when ch
          (comm/on-text-chunk ch session-key output)
          (comm/on-turn-end ch session-key (assoc result :content output)))
        result)
      ((requiring-resolve 'isaac.drive.turn/run-turn!) state-dir session-key input opts))))

;; endregion ^^^^^ Triage ^^^^^
