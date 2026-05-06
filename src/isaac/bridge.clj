(ns isaac.bridge
  (:require
    [clojure.string :as str]
    [isaac.comm :as comm]
    [isaac.fs :as fs]
    [isaac.home :as home]
    [isaac.session.logging :as logging]
    [isaac.session.storage :as storage]
    [isaac.tool.registry :as tool-registry]))

;; TODO - MDM:  bridge does not belong in session.  Is not a child component.
;;  Move this file to issac.bridge

;; region ----- Cancellation -----

(defonce ^:private active-turns (atom {}))
(defonce ^:private pending-cancels (atom #{}))

(defn cancelled-result []
  {:stopReason "cancelled"})

(defn cancelled-response? [result]
  (= "cancelled" (:stopReason result)))

(defn begin-turn! [session-key]
  (let [turn {:cancelled? (atom false)
              :hooks      (atom [])}]
    (swap! active-turns assoc session-key turn)
    (when (contains? @pending-cancels session-key)
      (swap! pending-cancels disj session-key)
      (reset! (:cancelled? turn) true))
    (when (contains? @pending-cancels session-key)
      (swap! pending-cancels disj session-key)
      (reset! (:cancelled? turn) true))
    turn))

(defn end-turn! [session-key turn]
  (swap! active-turns #(if (identical? turn (get % session-key))
                         (dissoc % session-key)
                         %))
  nil)

(defn cancelled? [session-key]
  (or (contains? @pending-cancels session-key)
      (some-> (get @active-turns session-key) :cancelled? deref boolean)))

(defn on-cancel! [session-key f]
  (when (and session-key f)
    (if-let [turn (get @active-turns session-key)]
      (do
        (swap! (:hooks turn) conj f)
        (when @(:cancelled? turn)
          (f)))
      (when (contains? @pending-cancels session-key)
        (f))))
  nil)

(defn cancel! [session-key]
  (when session-key
    (if-let [turn (get @active-turns session-key)]
      (do
        (reset! (:cancelled? turn) true)
        (doseq [hook @(:hooks turn)]
          (try
            (hook)
            (catch Exception _ nil)))
        true)
      (do
        (swap! pending-cancels conj session-key)
        true))))

;; endregion ^^^^^ Cancellation ^^^^^

;; region ----- Status Command -----

(defn- turn-count [transcript]
  (count (filter #(= "message" (:type %)) transcript)))

(defn- ctx-provider-name [ctx]
  (let [p (:provider ctx)]
    (cond
      (string? p) p
      (some? p)   ((requiring-resolve 'isaac.provider/display-name) p)
      :else       nil)))

(defn status-data
  "Gather session and model info for the /status command."
  [state-dir session-key ctx]
  (let [entry          (storage/get-session state-dir session-key)
        transcript     (or (storage/get-transcript state-dir session-key) [])
        turns          (turn-count transcript)
        tokens         (or (:total-tokens entry) 0)
        context-window (or (:context-window ctx) 32768)
        context-pct    (if (pos? context-window)
                         (int (Math/round (* 100.0 (/ tokens context-window))))
                         0)]
    {:crew           (:crew ctx)
     :boot-files     (:boot-files ctx)
     :soul           (:soul ctx)
     :model          (:model ctx)
     :provider       (ctx-provider-name ctx)
     :session-key    session-key
     :session-file   (:session-file entry)
      :turns          turns
      :compactions    (or (:compaction-count entry) 0)
      :tokens         tokens
      :context-window context-window
      :context-pct    context-pct
     :tool-count     (count (tool-registry/all-tools))
     :cwd            (System/getProperty "user.dir")}))

(defn- summarize-soul [ctx]
  (let [soul    (or (:soul ctx) "")
        source  (if (> (count (remove str/blank? (str/split (str/trim soul) #"\s+"))) 4)
                  soul
                  (or (:boot-files ctx) soul ""))
        text   (-> source
                   (str/replace #"(?m)^#+\s.*$" "")
                   (str/replace #"\[([^\]]+)\]\([^)]+\)" "$1")
                   (str/replace #"`" "")
                   (str/replace #"\s+" " ")
                   str/trim)
        words  (->> (str/split text #"\s+")
                    (remove str/blank?)
                    vec)]
    (cond
      (empty? words)
      ""

      (<= (count words) 8)
      (str/join " " words)

      :else
      (str (str/join " " (take 8 words)) " ..."))))

(declare parse-command)

(defn available-commands []
  [{:description "Show session status" :name "status"}
   {:description "Show or switch model" :name "model"}
   {:description "Show or switch crew" :name "crew"}
   {:description "Show or set working directory" :name "cwd"}])

(defn format-status
  "Format status data as human-readable markdown-style status lines."
  [data]
  (let [label-width 12
        line        (fn [label value]
                      (format (str "%-" label-width "s %s") label value))]
    (str "```text\n"
         (str/join "\n"
                   ["Session Status"
                    (apply str (repeat 22 "─"))
                    (line "Crew"        (:crew data))
                    (line "Model"       (str (:model data) " (" (:provider data) ")"))
                    (line "Session"     (:session-key data))
                    (line "File"        (:session-file data))
                    (line "Turns"       (:turns data))
                    (line "Compactions" (:compactions data))
                    (line "Context"     (str (format "%,d" (:tokens data)) " / "
                                              (format "%,d" (:context-window data)) " ("
                                              (:context-pct data) "%)"))
                    (line "Soul"        (str "\"" (summarize-soul data) "\""))
                    (line "Tools"       (:tool-count data))
                    (line "CWD"         (:cwd data))])
         "\n```") ))

(defn- find-alias [models model provider]
  (some (fn [[alias cfg]]
          (when (and (= model (:model cfg)) (= provider (:provider cfg)))
            (if (keyword? alias) (name alias) (str alias))))
        models))

(defn- handle-model [state-dir session-key input ctx]
  (let [{:keys [args]} (parse-command input)
        models         (:models ctx)]
    (if (str/blank? args)
      (let [model    (:model ctx)
            provider (ctx-provider-name ctx)
            alias    (or (find-alias models model provider) model)]
        {:type    :command
         :command :model
         :message (str alias " (" provider "/" model ") is the current model")})
      (if-let [model-cfg (or (get models args)
                             (get models (keyword args)))]
        (do
          (storage/update-session! state-dir session-key {:model    (:model model-cfg)
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
            (storage/update-session! state-dir session-key {:crew     args
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
      (let [cwd (:cwd (storage/get-session state-dir session-key))]
        {:type    :command
         :command :cwd
         :message (str "current directory: " (or cwd "(not set)"))})
      (let [resolved (resolve-cwd-path state-dir args)]
        (if (fs/dir? resolved)
          (do
            (storage/update-session! state-dir session-key {:cwd resolved})
            {:type    :command
             :command :cwd
             :message (str "working directory set to " resolved)})
          {:type    :command
           :command :unknown
           :message (str "no such directory: " args)})))))

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

      "crew"
      (handle-crew state-dir session-key input ctx)

      "model"
      (handle-model state-dir session-key input ctx)

      "cwd"
      (handle-cwd state-dir session-key input ctx)

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

(defn- slash-ctx [state-dir session-key opts]
  (let [session (storage/get-session state-dir session-key)]
    (assoc (select-keys opts [:model :provider :soul :context-window :models :crew-members :boot-files])
           :crew (or (:crew session) "main"))))

(defn dispatch!
  "Comm-facing entry point. Slash commands are handled here; normal turns
   delegate to run-turn!. Bridge → drive direction only."
  [state-dir session-key input opts]
  (if (slash-command? input)
    (let [ch      (:comm opts)
          ctx     (slash-ctx state-dir session-key opts)
          result  (handle-slash state-dir session-key input ctx)
          output  (if (= :status (:command result))
                    (format-status (:data result))
                    (:message result))]
      (when ch
        (comm/on-text-chunk ch session-key output)
        (comm/on-turn-end ch session-key (assoc result :content output)))
      result)
    ((requiring-resolve 'isaac.drive.turn/run-turn!) state-dir session-key input opts)))

;; endregion ^^^^^ Triage ^^^^^
