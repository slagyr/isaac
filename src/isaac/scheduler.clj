(ns isaac.scheduler
  (:require
    [c3kit.apron.schema :as schema]
    [isaac.cron.cron :as cron])
  (:import
    (java.time Instant OffsetDateTime ZoneId ZonedDateTime)))

(def ^:private default-tick-ms 50)

(def trigger-schema
  {:name   :scheduler-trigger
   :type   :map
   :schema {:kind    {:type :keyword :required? true :validate schema/present? :message "must be present"}
            :ms      {:type :long}
            :expr    {:type :string}
            :zone    {:type :string}
            :instant {:type :ignore}}})

(def task-schema
  {:name   :scheduler-task
   :type   :map
   :schema {:id         {:type :keyword :required? true :validate schema/present? :message "must be present"}
            :trigger    {:type :map :required? true :schema (:schema trigger-schema) :validate schema/present? :message "must be present"}
            :handler    {:type :ignore :required? true}
            :coalesce   {:type :keyword}
            :on-error   {:type :keyword}
            :backoff-ms {:type :long}
            :timeout-ms {:type :long}}})

(defn- parse-instant [value]
  (cond
    (instance? Instant value) value
    (instance? OffsetDateTime value) (.toInstant ^OffsetDateTime value)
    (string? value) (try
                      (Instant/parse value)
                      (catch Exception _
                        (.toInstant (OffsetDateTime/parse value))))
    :else (throw (ex-info "unsupported instant value" {:value value}))))

(defn- validate-trigger! [{:keys [kind ms expr instant]}]
  (case kind
    :interval (when-not (pos? (or ms 0))
                (throw (ex-info "interval trigger requires positive :ms" {:trigger {:kind kind :ms ms}})))
    :delay    (when-not (pos? (or ms 0))
                (throw (ex-info "delay trigger requires positive :ms" {:trigger {:kind kind :ms ms}})))
    :cron     (when-not expr
                (throw (ex-info "cron trigger requires :expr" {:trigger {:kind kind :expr expr}})))
    :at       (when-not instant
                (throw (ex-info "at trigger requires :instant" {:trigger {:kind kind :instant instant}})))
    (throw (ex-info (str "unsupported trigger kind: " kind) {:trigger {:kind kind}}))))

(defn- validate-task! [{:keys [handler trigger] :as task}]
  (let [task (schema/conform! task-schema task)]
    (when-not (fn? handler)
      (throw (ex-info "task handler must be a function" {:task (select-keys task [:id :handler])})))
    (validate-trigger! trigger)
    task))

(defn- cron-next-time [{:keys [expr zone]} reference]
  (let [zone-id       (ZoneId/of (or zone (str (ZoneId/systemDefault))))
        reference-zdt (ZonedDateTime/ofInstant reference zone-id)]
    (some-> (cron/next-fire-at expr reference-zdt reference-zdt zone-id)
            .toInstant)))

(defn- next-time [{:keys [kind ms expr zone instant]} now]
  (case kind
    :interval (.plusMillis now ms)
    :delay    (.plusMillis now ms)
    :cron     (cron-next-time {:expr expr :zone zone} now)
    :at       (parse-instant instant)
    (throw (ex-info (str "unsupported trigger kind: " kind) {:trigger {:kind kind :ms ms}}))))

(defn create
  "Creates a scheduler runtime value.

   The returned scheduler is explicit state passed to `schedule!`, `tick!`,
   `start!`, and `stop!`. Integration layers may also register it in
   `isaac.system` as the process-wide shared scheduler."
  [{:keys [clock]}]
  {:clock    (or clock #(Instant/now))
   :tick-ms  default-tick-ms
   :tasks    (atom {})
   :running? (atom false)
   :runner   (atom nil)})

(defn running?
  "Returns true when the scheduler's background tick loop is running."
  [scheduler]
  (boolean @(:running? scheduler)))

(defn list-tasks
  "Returns the currently scheduled tasks in registration order."
  [scheduler]
  (->> @(:tasks scheduler)
       vals
       (sort-by :created-at)
       vec))

(defn schedule!
  "Registers a repeating or one-shot task.

   Task shape is validated against `task-schema`. Re-registering an existing
   `:id` throws."
  [scheduler task]
  (let [now  ((:clock scheduler))
        task (assoc (validate-task! task)
                :created-at now
                :next-fire-at (next-time (:trigger task) now)
                :remaining-fires (when (#{:delay :at} (get-in task [:trigger :kind])) 1))]
    (swap! (:tasks scheduler)
           (fn [tasks]
              (when (contains? tasks (:id task))
                (throw (ex-info (str "task already scheduled: " (:id task)) {:id (:id task)})))
              (assoc tasks (:id task) task)))
    task))

(defn cancel!
  "Cancels the task with the given id. Unknown ids are a silent no-op."
  [scheduler id]
  (swap! (:tasks scheduler) dissoc id)
  nil)

(defn schedule-once!
  "Registers a one-shot task. Alias for `schedule!`; one-shot semantics come
   from `:delay` and `:at` triggers."
  [scheduler task]
  (schedule! scheduler task))

(defn tick!
  "Runs all tasks whose `:next-fire-at` is due according to the scheduler clock."
  [scheduler]
  (let [now ((:clock scheduler))]
    (doseq [[id task] @(:tasks scheduler)]
      (loop [task task]
        (when (and task (not (.isAfter ^Instant (:next-fire-at task) now)))
          ((:handler task) {:id id :scheduled-at (:next-fire-at task) :now now})
          (if (#{:delay :at} (get-in task [:trigger :kind]))
            (swap! (:tasks scheduler) dissoc id)
            (let [updated (assoc task :next-fire-at (next-time (:trigger task) (:next-fire-at task)))]
              (swap! (:tasks scheduler) assoc id updated)
              (recur updated)))))))
  nil)

(defn start!
  "Starts the scheduler's background tick loop if it is not already running."
  [scheduler]
  (when-not (running? scheduler)
    (reset! (:running? scheduler) true)
    (reset! (:runner scheduler)
            (future
              (while @(:running? scheduler)
                (tick! scheduler)
                (Thread/sleep (:tick-ms scheduler))))))
  scheduler)

(defn stop!
  "Stops the background tick loop and clears all registered tasks."
  [scheduler]
  (reset! (:running? scheduler) false)
  (when-let [runner @(:runner scheduler)]
    (future-cancel runner))
  (reset! (:runner scheduler) nil)
  (reset! (:tasks scheduler) {})
  nil)
