(ns isaac.cli.sessions
  (:require
    [clojure.string :as str]
    [clojure.tools.cli :as tools-cli]
    [isaac.cli.registry :as registry]
    [isaac.config.resolution :as config]
    [isaac.session.storage :as storage])
  (:import
    (java.time Instant)
    (java.time.format DateTimeFormatter)
    (java.time ZoneOffset)))

(def option-spec
  [["-a" "--agent NAME" "Filter to a specific agent"]
   ["-h" "--help"       "Show help"]])

(defn- parse-option-map [raw-args]
  (let [{:keys [options errors]} (tools-cli/parse-opts raw-args option-spec)]
    {:options (->> options (remove (comp nil? val)) (into {}))
     :errors  errors}))

;; region ----- Formatting -----

(defn format-age
  "Format age in milliseconds as a human-readable relative string."
  [age-ms]
  (let [secs (quot age-ms 1000)
        mins (quot secs 60)
        hrs  (quot mins 60)
        days (quot hrs 24)]
    (cond
      (>= days 1)  (str days "d ago")
      (>= hrs 1)   (str hrs "h ago")
      (>= mins 1)  (str mins "m ago")
      :else        (str secs "s ago"))))

(defn- parse-iso [ts]
  (try
    (-> (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss")
        (.parse ts)
        (Instant/from))
    (catch Exception _ nil)))

(defn- age-ms [updated-at]
  (when-let [inst (parse-iso updated-at)]
    (- (System/currentTimeMillis) (.toEpochMilli inst))))

(defn format-context
  "Format token usage as 'tokens / context-window (pct%)'."
  [tokens context-window]
  (let [tokens (or tokens 0)
        pct    (if (pos? context-window)
                 (int (Math/round (* 100.0 (/ tokens context-window))))
                 0)]
    (str (format "%,d" tokens) " / " (format "%,d" context-window) " (" pct "%)")))

(defn- format-session-row [entry context-window]
  (let [key-str    (:key entry)
        tokens     (or (:totalTokens entry) 0)
        updated-at (:updatedAt entry)
        age-str    (if-let [ms (age-ms updated-at)]
                     (format-age ms)
                     "-")
        ctx-str    (format-context tokens context-window)]
    (str "  " key-str "  " age-str "  " ctx-str)))

;; endregion ^^^^^ Formatting ^^^^^

;; region ----- Data -----

(defn list-all
  "Returns a map of agent-id -> sessions (sorted by updatedAt desc).
   When agent-filter is provided, only that agent is included."
  [state-dir agent-filter]
  (let [agents (if agent-filter
                 [agent-filter]
                 (storage/list-agents state-dir))]
    (->> agents
         (map (fn [agent-id]
                [agent-id (->> (storage/list-sessions state-dir agent-id)
                               (sort-by :updatedAt)
                               reverse
                               vec)]))
         (into {}))))

;; endregion ^^^^^ Data ^^^^^

;; region ----- Output -----

(defn- resolve-context-window [state-dir agent-id]
  (let [cfg (config/load-config)]
    (or (get-in cfg [:agents :models]) 32768)
    32768))

(defn- print-agent-sessions [state-dir agent-id sessions]
  (println (str "agent: " agent-id))
  (if (empty? sessions)
    (println "  (no sessions)")
    (let [cw (resolve-context-window state-dir agent-id)]
      (doseq [entry sessions]
        (println (format-session-row entry cw))))))

;; endregion ^^^^^ Output ^^^^^

;; region ----- Command -----

(defn run [opts]
  (let [state-dir    (or (:state-dir opts)
                         (:stateDir (config/load-config))
                         (str (System/getProperty "user.home") "/.isaac"))
        agent-filter (:agent opts)]
    (if (and agent-filter
             (not (some #{agent-filter} (storage/list-agents state-dir))))
      (do
        (binding [*out* *err*]
          (println (str "unknown agent: " agent-filter)))
        1)
      (let [sessions-by-agent (list-all state-dir agent-filter)]
        (if (empty? sessions-by-agent)
          (println "no sessions found")
          (doseq [[agent-id sessions] (sort-by key sessions-by-agent)]
            (print-agent-sessions state-dir agent-id sessions)))
        0))))

(defn run-fn [{:keys [_raw-args] :as opts}]
  (let [{:keys [options errors]} (parse-option-map (or _raw-args []))]
    (cond
      (:help options)
      (do
        (println (registry/command-help (registry/get-command "sessions")))
        0)

      (seq errors)
      (do
        (doseq [error errors] (println error))
        1)

      :else
      (run (merge (dissoc opts :_raw-args) options)))))

(registry/register!
  {:name        "sessions"
   :usage       "sessions [options]"
   :desc        "List stored conversation sessions"
   :option-spec option-spec
   :run-fn      run-fn})

;; endregion ^^^^^ Command ^^^^^
