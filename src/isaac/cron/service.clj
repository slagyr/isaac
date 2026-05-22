;; mutation-tested: 2026-05-06
(ns isaac.cron.service
  (:require
    [isaac.bridge.core :as bridge]
    [isaac.charge :as charge]
    [isaac.comm.null :as null-comm]
    [isaac.config.loader :as config]
    [isaac.configurator :as configurator]
    [isaac.scheduler.cron :as cron]
     [isaac.cron.state :as state]
     [isaac.logger :as log]
     [isaac.scheduler :as scheduler]
     [isaac.session.context :as session-ctx]
     [isaac.session.store :as store]
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
    now (ZonedDateTime/ofInstant now zone)
    :else (ZonedDateTime/ofInstant (memory/now) zone)))

(defn- effective-runtime-ctx [{:keys [state-dir session-store]}]
  (let [runtime (store/runtime-ctx)]
    {:state-dir     (or state-dir (:state-dir runtime))
     :session-store (or session-store (:session-store runtime))}))

(defn- fire-job! [ctx cfg job-name {:keys [crew prompt]} scheduled-at]
  (let [state-dir      (:state-dir ctx)
        session-store* (store/resolve-store ctx "cron scheduler")
        session        (session-ctx/create-with-resolved-behavior!
                         nil {:cfg           cfg
                             :crew          crew
                             :state-dir     state-dir
                             :home          state-dir
                             :origin        {:kind :cron :name (str job-name)}
                             :session-store session-store*})
        result         (binding [memory/*now* (.toInstant scheduled-at)]
                         (bridge/dispatch!
                           (charge/build {:session-key   (:id session)
                                          :input         prompt
                                          :cfg           cfg
                                          :state-dir     state-dir
                                          :session-store session-store*
                                          :home          state-dir
                                          :crew          crew
                                          :origin        {:kind :cron :name (str job-name)}
                                          :comm          null-comm/channel})))
        failed?   (boolean (:error result))]
    (state/write-job-state! state-dir job-name {:last-run    (cron/format-zoned-date-time scheduled-at)
                                                :last-status (if failed? :failed :succeeded)
                                                :last-error  (when failed?
                                                               (or (:message result)
                                                                   (some-> (:error result) str)))})
    result))

(defn- handle-scheduled-job! [ctx cfg tick-ms job-name job {:keys [scheduled-at now]}]
  (let [zone          (zone-id cfg)
        scheduled-zdt (ZonedDateTime/ofInstant scheduled-at zone)
        now-zdt       (normalized-now now zone)]
    (if (< (cron/late-by-ms scheduled-zdt now-zdt) tick-ms)
      (try
        (fire-job! ctx cfg job-name job scheduled-zdt)
        (catch Exception e
          (log/ex :cron/job-failed e :job (str job-name))
          (state/write-job-state! (:state-dir ctx) job-name {:last-run    (cron/format-zoned-date-time scheduled-zdt)
                                                              :last-status :failed
                                                              :last-error  (.getMessage e)})))
      (log/warn :cron/missed-schedule
                :job (str job-name)
                :scheduled-at (cron/format-zoned-date-time scheduled-zdt)))))

(defn start! [{:keys [cfg state-dir session-store tick-ms]
                 :or   {tick-ms default-tick-ms}}]
  (let [{:keys [state-dir session-store]} (effective-runtime-ctx {:state-dir state-dir :session-store session-store})
        shared-scheduler (or (system/get :scheduler)
                             (throw (ex-info "cron scheduler requires :scheduler in isaac.system" {})))
        runtime-ctx      {:state-dir state-dir :session-store session-store}
        zone             (str (zone-id cfg))
        task-ids         (reduce (fn [ids [job-name job]]
                                   (scheduler/schedule! shared-scheduler
                                                         {:id      (task-id job-name)
                                                          :trigger {:kind :cron :expr (:expr job) :zone zone}
                                                          :handler (fn [scheduler-ctx]
                                                                     (handle-scheduled-job! runtime-ctx cfg tick-ms job-name job scheduler-ctx))})
                                    (conj ids (task-id job-name)))
                                  []
                                  (cron-jobs cfg))]
    {:scheduler shared-scheduler
     :task-ids  task-ids}))

(defn stop! [{:keys [scheduler task-ids]}]
  (doseq [id task-ids]
    (scheduler/cancel! scheduler id)))
