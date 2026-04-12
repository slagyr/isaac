(ns isaac.session.bridge
  (:require
    [clojure.string :as str]
    [isaac.session.storage :as storage]
    [isaac.tool.registry :as tool-registry]))

;; region ----- Status Command -----

(defn- user-turns [transcript]
  (count (filter #(= "user" (get-in % [:message :role])) transcript)))

(defn status-data
  "Gather session and model info for the /status command."
  [state-dir session-key ctx]
  (let [entry          (storage/get-session state-dir session-key)
        transcript     (or (storage/get-transcript state-dir session-key) [])
        turns          (user-turns transcript)
        tokens         (or (:totalTokens entry) 0)
        context-window (or (:context-window ctx) 32768)
        context-pct    (if (pos? context-window)
                         (int (Math/round (* 100.0 (/ tokens context-window))))
                         0)]
    {:agent          (:agent ctx)
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

(defn format-status
  "Format status data as human-readable key: value lines."
  [data]
  (str/join "\n"
            [(str "agent: "          (:agent data))
             (str "model: "          (:model data))
             (str "provider: "       (:provider data))
             (str "session-key: "    (:session-key data))
             (str "session-file: "   (:session-file data))
             (str "turns: "          (:turns data))
             (str "compactions: "    (:compactions data))
             (str "tokens: "         (:tokens data))
             (str "context-window: " (:context-window data))
             (str "context-pct: "    (:context-pct data))
             (str "tool-count: "     (:tool-count data))
             (str "cwd: "            (:cwd data))]))

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

      {:type    :command
       :command :unknown
       :message (str "Unknown command: /" name)})))

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
