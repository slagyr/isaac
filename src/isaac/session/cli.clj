(ns isaac.session.cli
  (:require
    [clojure.string :as str]
    [clojure.tools.cli :as tools-cli]
    [isaac.cli :as registry]
    [isaac.config.loader :as config]
    [isaac.bridge :as bridge]
    [isaac.session.context :as session-ctx]
    [isaac.session.storage :as storage])
  (:import
    (java.time.format DateTimeFormatter)
    (java.time ZoneOffset)))

(def option-spec
  [["-c" "--crew NAME"  "Filter to a specific crew member"]
   ["-h" "--help"       "Show help"]])

(defn- parse-option-map [raw-args]
  (let [{:keys [options errors]} (tools-cli/parse-opts raw-args option-spec)]
    {:options (->> options (remove (comp nil? val)) (into {}))
     :errors  errors}))

;; region ----- Formatting -----

(defn format-age
  "Format age in milliseconds as a compact relative string."
  [age-ms]
  (let [secs (quot age-ms 1000)
        mins (quot secs 60)
        hrs  (quot mins 60)
        days (quot hrs 24)]
    (cond
      (>= days 1)  (str days "d")
      (>= hrs 1)   (str hrs "h")
      (>= mins 1)  (str mins "m")
      :else        (str secs "s"))))

(defn- parse-iso [ts]
  (try
    (-> (java.time.LocalDateTime/parse ts (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss"))
        (.toInstant ZoneOffset/UTC))
    (catch Exception _ nil)))

(defn age-ms [updated-at]
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
  (let [key-str    (or (:key entry) (:id entry))
        tokens     (or (:total-tokens entry) 0)
        updated-at (:updated-at entry)
        age-str    (if-let [ms (age-ms updated-at)] (format-age ms) "-")
        used-str   (format "%,d" tokens)
        window-str (format "%,d" context-window)
        pct        (if (pos? context-window)
                     (int (Math/round (* 100.0 (/ tokens context-window)))) 0)
        pct-str    (str pct "%")]
    (format "  %-22s  %8s  %8s  %8s  %4s" key-str age-str used-str window-str pct-str)))

;; endregion ^^^^^ Formatting ^^^^^

;; region ----- Data -----

(defn list-all
  "Returns a map of crew-id -> sessions (sorted by updated-at desc).
   Sessions without an explicit crew are grouped under 'main'.
   When crew-filter is provided, only that crew member is included."
  [state-dir crew-filter]
  (->> (storage/list-sessions state-dir)
       (filter #(if crew-filter (= crew-filter (or (:crew %) "main")) true))
       (group-by #(or (:crew %) "main"))
       (map (fn [[crew-id sessions]]
               [crew-id (->> sessions (sort-by :updated-at) reverse vec)]))
       (into {})))

;; endregion ^^^^^ Data ^^^^^

;; region ----- Output -----

(defn- build-cfg [crew models]
  {:crew   (into {} (map (fn [[id a]]
                           [(str id)
                            (cond-> {}
                              (:soul a)  (assoc :soul (:soul a))
                              (:model a) (assoc :model (:model a)))])
                          crew))
   :models (into {} (map (fn [[id m]]
                           [(str id)
                            {:model         (:model m)
                             :provider      (:provider m)
                             :context-window (:context-window m)}])
                         models))})

(defn- resolve-context-window [cfg crew-id]
  (let [cfg       (config/normalize-config cfg)
        crew      (get-in cfg [:crew crew-id])
        model-id  (or (:model crew) (get-in cfg [:defaults :model]))
        model-cfg (get-in cfg [:models model-id])]
    (or (:context-window model-cfg) 32768)))

(def ^:private header-row
  (format "  %-22s  %8s  %8s  %8s  %4s" "SESSION" "AGE" "USED" "WINDOW" "PCT"))

(defn- print-crew-sessions [crew-id sessions cfg]
  (println (str "crew: " crew-id))
  (if (empty? sessions)
    (println "  (no sessions)")
    (let [cw (resolve-context-window cfg crew-id)]
      (println header-row)
      (doseq [entry sessions]
        (println (format-session-row entry cw))))))

;; endregion ^^^^^ Output ^^^^^

(defn- resolve-state-dir [opts loaded-cfg]
  (or (:state-dir opts)
      (:stateDir loaded-cfg)
      (str (System/getProperty "user.home") "/.isaac")))

(defn- run-show [opts session-id]
  (if (str/blank? session-id)
    (do (println "Usage: isaac sessions show <session-id>") 1)
    (let [loaded-cfg (config/normalize-config (config/load-config {:home (:home opts)}))
          state-dir  (resolve-state-dir opts loaded-cfg)
          session    (storage/get-session state-dir session-id)]
      (if (nil? session)
        (do (println (str "session not found: " session-id)) 1)
        (let [crew-id (:crew session "main")
              cfg     loaded-cfg
              ctx     (assoc (session-ctx/resolve-turn-context
                               {:crew-members (or (:crew cfg) {})
                                :models       (or (:models cfg) {})
                                :cwd          (:cwd session)
                                :home         state-dir}
                               crew-id)
                             :crew crew-id)
              status  (bridge/status-data state-dir session-id ctx)]
          (println (bridge/format-status status))
          0)))))

(defn- run-delete [opts session-id]
  (if (str/blank? session-id)
    (do (println "Usage: isaac sessions delete <session-id>") 1)
    (let [loaded-cfg (config/normalize-config (config/load-config {:home (:home opts)}))
          state-dir  (resolve-state-dir opts loaded-cfg)]
      (if (storage/delete-session! state-dir session-id)
        (do (println (str "deleted: " session-id)) 0)
        (do (println (str "session not found: " session-id)) 1)))))

;; region ----- Command -----

(defn run [opts]
  (let [injected-crew (when (map? (:crew opts)) (:crew opts))
        injected-agents (when (map? (:agents opts)) (:agents opts))
        loaded-cfg    (config/normalize-config (config/load-config {:home (:home opts)}))
        state-dir     (or (:state-dir opts)
                          (:stateDir loaded-cfg)
                          (str (System/getProperty "user.home") "/.isaac"))
        crew-filter   (when (string? (:crew opts)) (:crew opts))
        cfg           (if (or injected-crew injected-agents)
                         (build-cfg (or injected-crew injected-agents) (:models opts))
                         loaded-cfg)]
    (if (and crew-filter
             (not (contains? (:crew (config/normalize-config cfg)) crew-filter)))
      (do
        (binding [*out* *err*]
          (println (str "unknown crew: " crew-filter)))
        1)
      (let [sessions-by-crew (list-all state-dir crew-filter)]
        (if (empty? sessions-by-crew)
          (println "no sessions found")
          (doseq [[crew-id sessions] (sort-by key sessions-by-crew)]
            (print-crew-sessions crew-id sessions cfg)))
        0))))

(defn run-fn [{:keys [_raw-args] :as opts}]
  (let [subcmd (first _raw-args)]
    (cond
      (= "show" subcmd)
      (run-show opts (second _raw-args))

      (= "delete" subcmd)
      (run-delete opts (second _raw-args))

      :else
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
          (run (merge (dissoc opts :_raw-args) options)))))))

(registry/register!
  {:name        "sessions"
   :usage       "sessions [options]"
   :desc        "List stored conversation sessions"
   :option-spec option-spec
   :run-fn      run-fn})

;; endregion ^^^^^ Command ^^^^^
