(ns isaac.session.cli
  (:require
    [clojure.string :as str]
    [clojure.tools.cli :as tools-cli]
    [isaac.cli :as registry]
    [isaac.cli.common :as cli-common]
    [isaac.cli.table :as table]
    [isaac.config.loader :as config]
    [isaac.bridge.status :as bridge]
    [isaac.session.context :as session-ctx]
    [isaac.session.store :as store]
    [isaac.system :as system])
  (:import
    (java.time.format DateTimeFormatter)
    (java.time ZoneOffset)))

(def option-spec
  [["-c" "--crew NAME"          "Filter to a specific crew member"]
   [nil  "--color MODE"         "Color output: auto, always, never" :default "auto"]
   [nil  "--json"               "Output result as JSON"]
   [nil  "--edn"                "Output result as EDN"]
   [nil  "--tag TAG"            "Filter to sessions carrying this tag (repeatable)"
    :assoc-fn (fn [m k v] (update m k (fnil conj []) v))]
   [nil  "--in-flight"          "Show only in-flight sessions"]
   [nil  "--not-in-flight"      "Show only idle sessions"]
   [nil  "--no-color"           "Disable color output"]
   ["-h" "--help"               "Show help"]])

(defn- text-tags [tags]
  (if (seq tags)
    (->> tags (sort-by str) (map pr-str) (str/join " "))
    ""))

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

(def ^:private session-columns
  [{:key :name   :header "SESSION" :align :left}
   {:key :age    :header "AGE"     :align :right}
   {:key :used   :header "USED"    :align :right
    :format #(format "%,d" (or % 0))}
   {:key :window :header "WINDOW"  :align :right
    :format #(format "%,d" (or % 0))}
   {:key :pct    :header "PCT"     :align :right
     :format #(str (or % 0) "%")
     :color-fn (fn [p]
                 (let [p (or p 0)]
                   (cond (> p 100) :red (>= p 80) :yellow :else nil)))}])

(def ^:private tagged-session-columns
  [{:key :name   :header "Name"    :align :left}
   {:key :age    :header "AGE"     :align :right}
   {:key :used   :header "USED"    :align :right
    :format #(format "%,d" (or % 0))}
   {:key :window :header "WINDOW"  :align :right
    :format #(format "%,d" (or % 0))}
   {:key :pct    :header "PCT"     :align :right
    :format #(str (or % 0) "%")
    :color-fn (fn [p]
                (let [p (or p 0)]
                  (cond (> p 100) :red (>= p 80) :yellow :else nil)))}
   {:key :crew   :header "Crew"    :align :left}
   {:key :tags   :header "Tags"    :align :left}])

(defn- session->row [entry context-window session-store]
  (let [tokens (or (:last-input-tokens entry) 0)
        pct    (if (pos? context-window)
                  (int (Math/round (* 100.0 (/ tokens context-window)))) 0)
        name   (or (:key entry) (:id entry))]
    {:name   (str name (when (store/in-flight? session-store (:id entry)) " ✈️"))
      :age    (if-let [ms (age-ms (:updated-at entry))] (format-age ms) "-")
      :used   tokens
      :window context-window
      :pct    pct
      :crew   (or (:crew entry) "main")
      :tags   (text-tags (:tags entry))}))

(defn- effective-color? [options]
  (cond
    (:no-color options)            false
    (= "always" (:color options))  true
    (= "never"  (:color options))  false
    :else                          nil))   ; nil → table auto-detects TTY

;; endregion ^^^^^ Formatting ^^^^^

;; region ----- Data -----

(defn- session-store
  ([state-dir explicit-store]
   (store/resolve-store {:state-dir state-dir :session-store explicit-store} "sessions cli")))

(defn- session->payload [entry]
  (-> entry
      (assoc :name (or (:key entry) (:id entry)))
      (update :tags #(or % #{}))))

(defn- resolve-session-store [loaded-cfg state-dir]
  (or (system/get :session-store)
      (store/register! loaded-cfg state-dir)))

(defn list-all
  "Returns a map of crew-id -> sessions (sorted by updated-at desc).
    Sessions without an explicit crew are grouped under 'main'.
    When crew-filter is provided, only that crew member is included."
  ([crew-filter]
   (let [{:keys [state-dir session-store]} (store/runtime-ctx)]
     (list-all state-dir session-store crew-filter)))
  ([state-dir explicit-store crew-filter]
   (let [session-store (session-store state-dir explicit-store)]
     (->> (store/list-sessions session-store)
          (filter #(if crew-filter (= crew-filter (or (:crew %) "main")) true))
         (group-by #(or (:crew %) "main"))
         (map (fn [[crew-id sessions]]
                [crew-id (->> sessions (sort-by :updated-at) reverse vec)]))
         (into {})))))

;; endregion ^^^^^ Data ^^^^^

;; region ----- Output -----

(defn- resolve-context-window [cfg crew-id]
  (let [cfg       (config/normalize-config cfg)
        crew      (get-in cfg [:crew crew-id])
        model-id  (or (:model crew) (get-in cfg [:defaults :model]))
        model-cfg (get-in cfg [:models model-id])]
    (or (:context-window model-cfg) 32768)))

(defn- print-crew-sessions [crew-id sessions cfg color?]
  (println (str "crew: " crew-id))
  (if (empty? sessions)
    (println "  (no sessions)")
    (let [cw            (resolve-context-window cfg crew-id)
          session-store (or (system/get :session-store)
                            (store/resolve-store {:state-dir (system/get :state-dir)} "sessions cli render"))
          rows          (mapv #(session->row % cw session-store) sessions)
          columns       (if (some (comp seq :tags) sessions) tagged-session-columns session-columns)]
      (println (table/render {:columns  columns
                                 :rows     rows
                                 :zebra?   true
                                 :color?   color?})))))

(defn- print-session-data [value opts]
  (cond
    (:json opts) (cli-common/print-json! value)
    (:edn opts)  (cli-common/print-edn! value)
    :else        nil))

;; endregion ^^^^^ Output ^^^^^

(defn- resolve-state-dir [opts loaded-cfg]
  (or (:state-dir opts)
      (:stateDir loaded-cfg)
      (str (System/getProperty "user.home") "/.isaac")))

(defn- run-show [opts session-id]
  (if (str/blank? session-id)
    (do (println "Usage: isaac sessions show <session-id>") 1)
    (let [loaded-cfg (config/normalize-config (config/load-config {:home (:home opts)}))
          state-dir   (resolve-state-dir opts loaded-cfg)
          _           (system/register! :state-dir state-dir)
          session-store (resolve-session-store loaded-cfg state-dir)
          session     (store/get-session session-store session-id)]
      (if (nil? session)
        (do (println (str "session not found: " session-id)) 1)
        (if (or (:json opts) (:edn opts))
          (do
            (print-session-data (session->payload session) opts)
            0)
          (try
            (config/set-snapshot! loaded-cfg)
            (let [ctx    (binding [config/*isaac-home* state-dir]
                           (assoc (session-ctx/resolve-behavior session-id {:state-dir     state-dir
                                                                            :home          state-dir
                                                                            :session-store session-store})
                                   :boot-files (session-ctx/read-boot-files (:cwd session))
                                   :state-dir state-dir))
                  status (bridge/status-data session-id ctx)]
              (println (bridge/format-status status))
              0)
            (finally
              (config/set-snapshot! nil))))))))

(defn- run-delete [opts session-id]
  (if (str/blank? session-id)
    (do (println "Usage: isaac sessions delete <session-id>") 1)
    (let [loaded-cfg (config/normalize-config (config/load-config {:home (:home opts)}))
          state-dir  (resolve-state-dir opts loaded-cfg)
          _          (system/register! :state-dir state-dir)
          session-store (resolve-session-store loaded-cfg state-dir)]
      (if (store/delete-session! session-store session-id)
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
        _             (system/register! :state-dir state-dir)
        session-store (resolve-session-store loaded-cfg state-dir)
        crew-filter   (when (string? (:crew opts)) (:crew opts))
        cfg           (if (or injected-crew injected-agents)
                          (cli-common/build-cfg (or injected-crew injected-agents) (:models opts))
                          loaded-cfg)]
    (if (and (:in-flight opts) (:not-in-flight opts))
      (do
        (binding [*out* *err*]
          (println "--in-flight and --not-in-flight are mutually exclusive"))
        1)
      (if (and crew-filter
               (not (contains? (:crew (config/normalize-config cfg)) crew-filter)))
        (do
          (binding [*out* *err*]
            (println (str "unknown crew: " crew-filter)))
          1)
        (let [required-tags    (set (map keyword (:tag opts)))
              sessions         (->> (store/list-sessions session-store)
                                    (filter #(if crew-filter (= crew-filter (or (:crew %) "main")) true))
                                    (filter #(every? (fn [tag] (store/has-tag? % tag)) required-tags))
                                    (filter #(if (:in-flight opts) (store/in-flight? session-store (:id %)) true))
                                    (filter #(if (:not-in-flight opts) (not (store/in-flight? session-store (:id %))) true)))
              sessions-by-crew (->> sessions
                                    (group-by #(or (:crew %) "main"))
                                    (map (fn [[crew-id grouped]] [crew-id (->> grouped (sort-by :updated-at) reverse vec)]))
                                    (into {}))
              color?           (effective-color? opts)]
          (cond
            (and (or (:json opts) (:edn opts)) (empty? sessions-by-crew))
            (print-session-data [] opts)

            (or (:json opts) (:edn opts))
            (print-session-data
              (vec (for [[crew-id grouped] (sort-by key sessions-by-crew)
                         session            grouped]
                     (session->payload (assoc session :crew (or (:crew session) crew-id)))))
              opts)

            (empty? sessions-by-crew)
            (println "no sessions found")

            :else
            (doseq [[crew-id grouped] (sort-by key sessions-by-crew)]
              (print-crew-sessions crew-id grouped cfg color?)))
          0)))))

(defn run-fn [{:keys [_raw-args] :as opts}]
  (let [subcmd (first _raw-args)]
    (cond
      (= "show" subcmd)
      (let [{:keys [options errors]} (parse-option-map (drop 2 _raw-args))]
        (if (seq errors)
          (do
            (doseq [error errors] (println error))
            1)
          (run-show (merge (dissoc opts :_raw-args) options) (second _raw-args))))

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
