(ns isaac.cron.scheduler
  (:require
    [isaac.config.loader :as config]
    [isaac.cron.cron :as cron]
    [isaac.cron.state :as state]
    [isaac.drive.turn :as turn]
    [isaac.logger :as log]
    [isaac.session.storage :as storage]
    [isaac.tool.memory :as memory])
  (:import
    (java.time ZoneId ZonedDateTime)))

(def ^:private default-tick-ms 30000)

(defn- zone-id [cfg]
  (if-let [tz (:tz cfg)]
    (ZoneId/of tz)
    (ZoneId/systemDefault)))

(defn- normalized-now [now zone]
  (cond
    (instance? ZonedDateTime now) (.withZoneSameInstant ^ZonedDateTime now zone)
    now                           (ZonedDateTime/ofInstant now zone)
    :else                         (ZonedDateTime/ofInstant (memory/now) zone)))

(defn- job-context [cfg crew-id state-dir]
  (let [{:keys [context-window model provider provider-config soul]} (config/resolve-crew-context cfg crew-id {:home state-dir})]
    {:context-window context-window
     :crew-members   (:crew cfg)
     :model          model
     :models         (:models cfg)
     :provider       provider
     :provider-config (or provider-config {})
     :soul           soul}))

(defn- fire-job! [state-dir cfg job-name {:keys [crew prompt]} scheduled-at]
  (let [session (storage/create-session! state-dir nil {:crew crew
                                                        :origin {:kind :cron :name (str job-name)}})
        opts    (job-context cfg crew state-dir)
        result  (binding [memory/*now* (.toInstant scheduled-at)]
                  (let [captured (atom nil)]
                    (with-out-str
                      (reset! captured (turn/process-user-input! state-dir (:id session) prompt opts)))
                     @captured))
        failed? (boolean (:error result))]
    (state/write-job-state! state-dir job-name {:last-run    (cron/format-zoned-date-time scheduled-at)
                                                :last-status (if failed? :failed :succeeded)
                                                :last-error  (when failed?
                                                               (or (:message result)
                                                                   (some-> (:error result) str)))})
    result))

(defn- last-processed-at [runtime job-name zone]
  (when-let [value (get @runtime (str job-name))]
    (cron/parse-zoned-date-time value zone)))

(defn- record-processed! [runtime job-name scheduled-at]
  (swap! runtime assoc (str job-name) (cron/format-zoned-date-time scheduled-at)))

(defn- evaluate-job! [runtime state-dir cfg now zone tick-ms [job-name job]]
  (let [runtime-state      (get (state/read-state state-dir) (str job-name))
        last-run-at        (when-let [last-run (:last-run runtime-state)]
                             (cron/parse-zoned-date-time last-run zone))
        last-processed     (or (last-processed-at runtime job-name zone) last-run-at)
        scheduled-at       (cron/previous-fire-at (:expr job) now zone)]
    (when (and scheduled-at
               (or (nil? last-processed)
                   (.isAfter scheduled-at last-processed)))
      (record-processed! runtime job-name scheduled-at)
      (if (< (cron/late-by-ms scheduled-at now) tick-ms)
        (try
          (fire-job! state-dir cfg job-name job scheduled-at)
          (catch Exception e
            (log/ex :cron/job-failed e :job (str job-name))
            (state/write-job-state! state-dir job-name {:last-run    (cron/format-zoned-date-time scheduled-at)
                                                        :last-status :failed
                                                        :last-error  (.getMessage e)})))
        (log/warn :cron/missed-schedule
                  :job (str job-name)
                  :scheduled-at (cron/format-zoned-date-time scheduled-at))))))

(defn tick!
  [{:keys [cfg now runtime state-dir tick-ms]
    :or   {cfg {} tick-ms default-tick-ms}}]
  (let [zone    (zone-id cfg)
        now     (normalized-now now zone)
        runtime (or runtime (atom {}))]
    (doseq [job-entry (sort-by first (or (:cron cfg) {}))]
      (evaluate-job! runtime state-dir cfg now zone tick-ms job-entry))
    runtime))

(defn start! [{:keys [cfg state-dir tick-ms]
               :or   {tick-ms default-tick-ms}}]
  (let [running? (atom true)
        runtime  (atom {})
        runner   (future
                   (while @running?
                     (tick! {:cfg       cfg
                             :runtime   runtime
                             :state-dir state-dir
                             :tick-ms   tick-ms})
                     (Thread/sleep tick-ms)))]
    {:running? running?
     :runner   runner
     :runtime  runtime}))

(defn stop! [{:keys [running? runner]}]
  (when running?
    (reset! running? false))
  (when runner
    (future-cancel runner)))
