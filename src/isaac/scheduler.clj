(ns isaac.scheduler
  (:require
    [isaac.cron.cron :as cron])
  (:import
    (java.time Instant ZoneId ZonedDateTime)))

(def ^:private default-tick-ms 50)

(defn- cron-next-time [{:keys [expr zone]} reference]
  (let [zone-id       (ZoneId/of (or zone (str (ZoneId/systemDefault))))
        reference-zdt (ZonedDateTime/ofInstant reference zone-id)]
    (some-> (cron/next-fire-at expr reference-zdt reference-zdt zone-id)
            .toInstant)))

(defn- next-time [{:keys [kind ms expr zone]} now]
  (case kind
    :interval (.plusMillis now ms)
    :delay    (.plusMillis now ms)
    :cron     (cron-next-time {:expr expr :zone zone} now)
    (throw (ex-info (str "unsupported trigger kind: " kind) {:trigger {:kind kind :ms ms}}))))

(defn create [{:keys [clock]}]
  {:clock    (or clock #(Instant/now))
   :tick-ms  default-tick-ms
   :tasks    (atom {})
   :running? (atom false)
   :runner   (atom nil)})

(defn running? [scheduler]
  (boolean @(:running? scheduler)))

(defn list-tasks [scheduler]
  (->> @(:tasks scheduler)
       vals
       (sort-by :created-at)
       vec))

(defn schedule! [scheduler {:keys [id trigger] :as task}]
  (let [now  ((:clock scheduler))
        task (assoc task
               :created-at now
               :next-fire-at (next-time trigger now)
               :remaining-fires (when (= :delay (:kind trigger)) 1))]
    (swap! (:tasks scheduler)
           (fn [tasks]
             (when (contains? tasks id)
               (throw (ex-info (str "task already scheduled: " id) {:id id})))
             (assoc tasks id task)))
    task))

(defn cancel! [scheduler id]
  (swap! (:tasks scheduler) dissoc id)
  nil)

(defn schedule-once! [scheduler task]
  (schedule! scheduler task))

(defn tick! [scheduler]
  (let [now ((:clock scheduler))]
    (doseq [[id task] @(:tasks scheduler)]
      (loop [task task]
        (when (and task (not (.isAfter ^Instant (:next-fire-at task) now)))
          ((:handler task) {:id id :scheduled-at (:next-fire-at task) :now now})
          (if (= :delay (get-in task [:trigger :kind]))
            (swap! (:tasks scheduler) dissoc id)
            (let [updated (assoc task :next-fire-at (next-time (:trigger task) (:next-fire-at task)))]
              (swap! (:tasks scheduler) assoc id updated)
              (recur updated)))))))
  nil)

(defn start! [scheduler]
  (when-not (running? scheduler)
    (reset! (:running? scheduler) true)
    (reset! (:runner scheduler)
            (future
              (while @(:running? scheduler)
                (tick! scheduler)
                (Thread/sleep (:tick-ms scheduler))))))
  scheduler)

(defn stop! [scheduler]
  (reset! (:running? scheduler) false)
  (when-let [runner @(:runner scheduler)]
    (future-cancel runner))
  (reset! (:runner scheduler) nil)
  (reset! (:tasks scheduler) {})
  nil)
