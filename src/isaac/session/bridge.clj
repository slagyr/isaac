(ns isaac.session.bridge
  (:require
    [clojure.string :as str]
    [isaac.session.storage :as storage]
    [isaac.tool.registry :as tool-registry]))

;; region ----- Status Command -----

(defn- turn-count [transcript]
  (count (filter #(= "message" (:type %)) transcript)))

(defn status-data
  "Gather session and model info for the /status command."
  [state-dir session-key ctx]
  (let [entry          (storage/get-session state-dir session-key)
        transcript     (or (storage/get-transcript state-dir session-key) [])
        turns          (turn-count transcript)
        tokens         (or (:totalTokens entry) 0)
        context-window (or (:context-window ctx) 32768)
        context-pct    (if (pos? context-window)
                         (int (Math/round (* 100.0 (/ tokens context-window))))
                         0)]
    {:agent          (:agent ctx)
     :soul           (:soul ctx)
      :model          (:model ctx)
      :provider       (:provider ctx)
      :session-key    session-key
      :session-file   (:sessionFile entry)
     :turns          turns
     :compactions    (or (:compactionCount entry) 0)
     :tokens         tokens
     :context-window context-window
     :context-pct    context-pct
      :tool-count     (count (tool-registry/all-tools))
      :cwd            (System/getProperty "user.dir")}))

(defn- context-bar [pct]
  (let [filled (max 1 (min 10 (int (Math/ceil (/ pct 10.0)))))]
    (apply str (repeat filled "█"))))

(declare parse-command)

(defn format-status
  "Format status data as human-readable key: value lines."
  [data]
  (str/join "\n"
            ["**Session Status**"
             (str "| Agent | " (:agent data))
             (str "| Model | " (:model data) " / " (:provider data))
             (str "| Session | " (:session-key data))
             (str "| File | " (:session-file data))
             (str "| Turns | " (:turns data))
             (str "| Compactions | " (:compactions data))
             (str "| Context | " (context-bar (:context-pct data)) " " (format "%,d" (:tokens data))
                  " / " (format "%,d" (:context-window data)) " (" (:context-pct data) "%)")
             (str "| Soul | SOUL.md|" (:soul data))
             (str "| Tools | " (:tool-count data))
             (str "| CWD | " (:cwd data))]))

(defn- model-data [ctx]
  (if-let [models (:models ctx)]
    (or (get models (:model ctx))
        (get models (keyword (:model ctx))))
    nil))

(defn- handle-model [state-dir session-key input ctx]
  (let [{:keys [args]} (parse-command input)]
    (if (str/blank? args)
      {:type    :command
       :command :model
       :message (str "model: " (:model ctx) "\nprovider: " (:provider ctx))}
      (if-let [model-cfg (or (get (:models ctx) args)
                             (get (:models ctx) (keyword args)))]
        (do
          (storage/update-session! state-dir session-key {:model (:model model-cfg)
                                                          :provider (:provider model-cfg)})
          {:type    :command
           :command :model
           :message (str "model: " (:model model-cfg) "\nprovider: " (:provider model-cfg))})
        {:type    :command
         :command :unknown
         :message (str "unknown model: " args)}))))

;; endregion ^^^^^ Status Command ^^^^^

;; region ----- Triage -----

(defn slash-command?
  "Returns true if input begins with a slash."
  [input]
  (and (string? input) (str/starts-with? input "/")))

(defn- parse-command [input]
  (let [parts (str/split (str/trim input) #"\s+" 2)
        cmd   (subs (first parts) 1)]
    {:name cmd :args (second parts)}))

(defn- handle-slash [state-dir session-key input ctx]
  (let [{:keys [name]} (parse-command input)]
    (case name
      "status"
      {:type    :command
       :command :status
       :data    (status-data state-dir session-key ctx)}

      "model"
      (handle-model state-dir session-key input ctx)

      {:type    :command
       :command :unknown
       :message (str "unknown command: /" name)})))

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

;; endregion ^^^^^ Triage ^^^^^
