;; mutation-tested: 2026-05-06
(ns isaac.cron.scheduler
  (:require
    [isaac.bridge.core :as bridge]
    [isaac.comm.null :as null-comm]
    [isaac.config.loader :as config]
    [isaac.configurator :as configurator]
    [isaac.cron.cron :as cron]
    [isaac.cron.state :as state]
    [isaac.logger :as log]
    [isaac.scheduler :as scheduler]
    [isaac.session.store.file :as file-store]
    [isaac.system :as system]
    [isaac.tool.memory :as memory])
  (:import
    (java.time ZoneId ZonedDateTime)))

(def ^:private default-tick-ms 30000)

(defn- task-id [job-name]
  (keyword "cron" (str job-name)))

(declare start! stop!)

(deftype CronModule [state-dir config* runner*]
  configurator/Reconfigurable
  (on-startup! [_ slice]
    (reset! config* (or slice {}))
    (when (seq slice)
      (reset! runner* (start! {:cfg (or (config/snapshot) {}) :state-dir state-dir}))))
  (on-config-change! [_ _old-slice new-slice]
    (when (not= @config* (or new-slice {}))
      (when-let [runner @runner*]
        (stop! runner))
      (reset! runner* nil)
      (reset! config* (or new-slice {}))
      (when (seq new-slice)
        (reset! runner* (start! {:cfg (or (config/snapshot) {}) :state-dir state-dir})))))
  Object
  (toString [_] "CronModule"))

(defn make [host]
  (->CronModule (:state-dir host) (atom {}) (atom nil)))

(def registry
  {:kind    :component
   :path    [:cron]
   :impl    "cron"
   :factory make})

(defn- ->job-name [job-name]
  (if (keyword? job-name) (name job-name) (str job-name)))

(defn- cron-jobs [cfg]
  (->> (reduce-kv (fn [jobs job-name job]
                    (assoc jobs (->job-name job-name) job))
                  (sorted-map)
                  (or (:cron cfg) {}))
       seq))

(defn job-state [instance job-name]
  (let [jobs @(.config* ^CronModule instance)]
    (or (get jobs job-name)
        (get jobs (keyword job-name)))))

(defn- zone-id [cfg]
  (if-let [tz (:tz cfg)]
    (ZoneId/of tz)
    (ZoneId/systemDefault)))

(defn- normalized-now [now zone]
  (cond
    (instance? ZonedDateTime now) (.withZoneSameInstant ^ZonedDateTime now zone)
    now                           (ZonedDateTime/ofInstant now zone)
    :else                         (ZonedDateTime/ofInstant (memory/now) zone)))

(defn- session-store []
  (or (system/get :session-store)
      (file-store/create-store (system/get :state-dir))))

(defn- fire-job! [cfg job-name {:keys [crew prompt]} scheduled-at]
  (let [session ((requiring-resolve 'isaac.session.context/create-with-resolved-behavior!)
                 nil {:cfg    cfg
                      :crew   crew
                      :origin {:kind :cron :name (str job-name)}})
        state-dir (system/get :state-dir)
        result  (binding [memory/*now* (.toInstant scheduled-at)]
                  (bridge/dispatch! {:session-key   (:id session)
                                     :input         prompt
                                     :cfg           cfg
                                     :home          state-dir
                                     :crew-override crew
                                     :origin        {:kind :cron :name (str job-name)}
                                     :comm          null-comm/channel}))
        failed? (boolean (:error result))]
    (state/write-job-state! job-name {:last-run    (cron/format-zoned-date-time scheduled-at)
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

(defn- evaluate-job! [runtime cfg now zone tick-ms [job-name job]]
  (let [runtime-state      (get (state/read-state) (str job-name))
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
          (fire-job! cfg job-name job scheduled-at)
          (catch Exception e
            (log/ex :cron/job-failed e :job (str job-name))
            (state/write-job-state! job-name {:last-run    (cron/format-zoned-date-time scheduled-at)
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
        runtime (or runtime (atom {}))
        run!    (fn []
                  (doseq [job-entry (cron-jobs cfg)]
                    (evaluate-job! runtime cfg now zone tick-ms job-entry))
                  runtime)]
    (if state-dir
      (system/with-nested-system {:state-dir state-dir} (run!))
      (run!))))

(defn- handle-scheduled-job! [cfg state-dir tick-ms job-name job {:keys [scheduled-at now]}]
  (let [zone          (zone-id cfg)
        scheduled-zdt (ZonedDateTime/ofInstant scheduled-at zone)
        now-zdt       (normalized-now now zone)]
    (if (< (cron/late-by-ms scheduled-zdt now-zdt) tick-ms)
      (try
        (if state-dir
          (system/with-nested-system {:state-dir state-dir}
            (fire-job! cfg job-name job scheduled-zdt))
          (fire-job! cfg job-name job scheduled-zdt))
        (catch Exception e
          (log/ex :cron/job-failed e :job (str job-name))
          (state/write-job-state! job-name {:last-run    (cron/format-zoned-date-time scheduled-zdt)
                                            :last-status :failed
                                            :last-error  (.getMessage e)})))
      (log/warn :cron/missed-schedule
                :job (str job-name)
                :scheduled-at (cron/format-zoned-date-time scheduled-zdt)))))

(defn start! [{:keys [cfg state-dir tick-ms]
                :or   {tick-ms default-tick-ms}}]
  (let [shared-scheduler (or (system/get :scheduler)
                             (throw (ex-info "cron scheduler requires :scheduler in isaac.system" {})))
        zone             (str (zone-id cfg))
        task-ids         (reduce (fn [ids [job-name job]]
                                   (scheduler/schedule! shared-scheduler
                                                        {:id      (task-id job-name)
                                                         :trigger {:kind :cron :expr (:expr job) :zone zone}
                                                         :handler (fn [ctx]
                                                                    (handle-scheduled-job! cfg state-dir tick-ms job-name job ctx))})
                                   (conj ids (task-id job-name)))
                                 []
                                 (cron-jobs cfg))]
    {:scheduler shared-scheduler
     :task-ids  task-ids}))

(defn stop! [{:keys [scheduler task-ids]}]
  (doseq [id task-ids]
    (scheduler/cancel! scheduler id)))
