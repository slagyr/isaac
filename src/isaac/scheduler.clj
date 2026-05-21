(ns isaac.scheduler
  (:require
    [c3kit.apron.schema :as schema]
    [c3kit.apron.schema.refs :as refs]
    [isaac.cron.cron :as cron]
    [isaac.logger :as log])
  (:import
    (java.time Instant OffsetDateTime ZoneId ZonedDateTime)
    (java.util UUID)))

(def ^:private default-tick-ms 50)

(defn- parse-instant [value]
  (cond
    (nil? value) nil
    (instance? Instant value) value
    (instance? OffsetDateTime value) (.toInstant ^OffsetDateTime value)
    (string? value) (try
                      (Instant/parse value)
                      (catch Exception _
                        (.toInstant (OffsetDateTime/parse value))))
    :else (throw (ex-info "unsupported instant value" {:value value}))))

(refs/ensure-installed!)

(def trigger-schema
  {:name   :scheduler-trigger
   :type   :map
   :schema {:kind    {:type :keyword :validations [[:one-of :interval :delay :cron :at]]
                      :description "Trigger kind: :interval, :delay, :cron, or :at"}
            :ms      {:type :long
                      :validations [[:maybe? :pos?]
                                    #_[:present-when? :kind :interval]
                                    #_{:scope    :entity
                                     :validate (fn [entity field-key]
                                                 (or (not= :interval (:kind entity))
                                                     (schema/present? (get entity field-key))))
                                     :message  "interval trigger requires positive :ms"}
                                    {:scope    :entity
                                     :validate (fn [entity field-key]
                                                 (or (not= :delay (:kind entity))
                                                     (schema/present? (get entity field-key))))
                                     :message  "delay trigger requires positive :ms"}]
                      :description "Relative delay or interval in milliseconds for :delay and :interval triggers"}
            :expr    {:type :string
                      :validations [{:scope    :entity
                                     :validate (fn [entity field-key]
                                                 (or (not= :cron (:kind entity))
                                                     (schema/present? (get entity field-key))))
                                     :message  "cron trigger requires :expr"}]
                      :description "Cron expression for :cron triggers"}
            :zone    {:type :string :description "IANA time zone name used to evaluate :cron triggers"}
            :instant {:type :ignore
                      :coerce [parse-instant]
                      :validations [{:scope    :entity
                                     :validate (fn [entity field-key]
                                                 (or (not= :at (:kind entity))
                                                     (schema/present? (get entity field-key))))
                                     :message  "at trigger requires :instant"}]
                      :description "Absolute instant for :at triggers; accepts java.time values or ISO-8601 strings"}}})

(def task-schema
  {:name   :scheduler-task
   :type   :map
   :schema {:id            {:type :keyword :required? true :validate schema/present? :message "must be present"
                            :description "Stable task identifier used for registration and cancellation"}
            :trigger       {:type :map :required? true :schema (:schema trigger-schema) :validate schema/present? :message "must be present"
                            :description "Scheduling trigger definition"}
            :handler       {:type :fn
                            :required? true
                            :validations [schema/required]
                            :description "Function invoked when the task fires"}
            :coalesce      {:type :keyword
                            :validations [{:validate #(contains? #{nil :queue :skip} %)
                                           :message  "must be one of [nil :queue :skip]"}]
                            :description "Overlap policy for due fires while a prior run is still active; supported values are :queue and :skip"}
            :on-error      {:type :keyword
                            :validations [{:validate #(contains? #{nil :log :retry-with-backoff :disable-after-N} %)
                                           :message  "must be one of [nil :log :retry-with-backoff :disable-after-N]"}]
                            :description "Handler failure policy; supported values are :log, :retry-with-backoff, and :disable-after-N"}
            :backoff-ms    {:type :long
                            :validations [{:validate #(or (nil? %) (pos? %))
                                           :message  ":backoff-ms must be positive"}
                                          {:scope    :entity
                                           :validate (fn [entity field-key]
                                                       (or (not= :retry-with-backoff (:on-error entity))
                                                           (schema/present? (get entity field-key))))
                                           :message  "retry-with-backoff requires positive :backoff-ms"}]
                            :description "Backoff delay in milliseconds used by :retry-with-backoff"}
            :disable-after {:type :long
                            :validations [{:validate #(or (nil? %) (pos? %))
                                           :message  ":disable-after must be positive"}
                                          {:scope    :entity
                                           :validate (fn [entity field-key]
                                                       (or (not= :disable-after-N (:on-error entity))
                                                           (schema/present? (get entity field-key))))
                                           :message  "disable-after-N requires positive :disable-after"}]
                            :description "Maximum consecutive failures before disabling a task when :on-error is :disable-after-N"}
            :timeout-ms    {:type :long
                            :validations [{:validate #(or (nil? %) (pos? %))
                                           :message  ":timeout-ms must be positive"}]
                            :description "Maximum runtime in milliseconds before interrupting a handler"}}})

(defn- cron-next-time [{:keys [expr zone]} reference]
  (let [zone-id       (ZoneId/of (or zone (str (ZoneId/systemDefault))))
        reference-zdt (ZonedDateTime/ofInstant reference zone-id)]
    (some-> (cron/next-fire-at expr reference-zdt reference-zdt zone-id)
            .toInstant)))

(defn- next-time [{:keys [kind ms expr zone instant]} now]
  (case kind
    :interval (.plusMillis now ms)
    :delay (.plusMillis now ms)
    :cron (cron-next-time {:expr expr :zone zone} now)
    :at instant
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

(defn- coalesce-mode [task]
  (or (:coalesce task) :queue))

(defn- on-error-mode [task]
  (or (:on-error task) :log))

(defn- done? [task]
  (and (nil? (:next-fire-at task))
       (empty? (:pending-fire-ats task))
       (nil? (:active-run task))))

(defn- task-name [id]
  (if-let [ns (namespace id)]
    (str ns "/" (name id))
    (name id)))

(declare finish-run! timeout-run! begin-run!)

(defn- build-run
  [scheduler id task scheduled-at]
  (let [token  (str (UUID/randomUUID))
        thread (doto
                 (Thread.
                   ^Runnable
                   (fn []
                     (try
                       ((:handler task) {:id id :scheduled-at scheduled-at :now ((:clock scheduler))})
                       (finish-run! scheduler id token :success nil scheduled-at)
                       (catch InterruptedException e
                         (finish-run! scheduler id token :interrupted e scheduled-at))
                       (catch Exception e
                         (finish-run! scheduler id token :error e scheduled-at)))))
                 (.setDaemon true)
                 (.setName (str "isaac-scheduler-" (task-name id))))]
    {:token token :thread thread :scheduled-at scheduled-at :timeout-ms (:timeout-ms task)}))

(defn- begin-run! [scheduler id run]
  (.start (:thread run))
  (when-let [timeout-ms (:timeout-ms run)]
    (doto
      (Thread.
        ^Runnable
        (fn []
          (Thread/sleep timeout-ms)
          (timeout-run! scheduler id (:token run) (:scheduled-at run))))
      (.setDaemon true)
      (.setName (str "isaac-scheduler-timeout-" (task-name id)))
      (.start))))

(defn- next-run-action [scheduler id task scheduled-at]
  (let [run (build-run scheduler id task scheduled-at)]
    {:id id :run run}))

(defn- update-after-error [task scheduled-at error]
  (let [consecutive-errors (inc (or (:consecutive-errors task) 0))
        task               (assoc task :consecutive-errors consecutive-errors)]
    (log/error :scheduler/handler-error
               :id (:id task)
               :scheduled-at (str scheduled-at)
               :error (.getMessage ^Exception error))
    (case (on-error-mode task)
      :retry-with-backoff
      (-> task
          (assoc :pending-fire-ats [])
          (assoc :next-fire-at (.plusMillis scheduled-at (:backoff-ms task))))

      :disable-after-N
      (if (>= consecutive-errors (:disable-after task))
        (do
          (log/warn :scheduler/disabled :id (:id task) :reason :too-many-errors)
          (assoc task :pending-fire-ats [] :next-fire-at nil :disabled? true))
        task)

      task)))

(defn- due-fires [task now]
  (loop [scheduled-at (:next-fire-at task)
         task         task
         fires        []]
    (if (and scheduled-at (not (.isAfter ^Instant scheduled-at now)))
      (if (#{:delay :at} (get-in task [:trigger :kind]))
        {:fires (conj fires scheduled-at)
         :task  (assoc task :next-fire-at nil :remaining-fires 0)}
        (let [next-at (next-time (:trigger task) scheduled-at)]
          (recur next-at
                 (assoc task :next-fire-at next-at)
                 (conj fires scheduled-at))))
      {:fires fires :task task})))

(defn- enqueue-fires [task fires]
  (if (= :skip (coalesce-mode task))
    task
    (update task :pending-fire-ats into fires)))

(defn- plan-due-run [scheduler id task fires]
  (let [task   (assoc task :pending-fire-ats (if (= :skip (coalesce-mode task)) [] (vec (rest fires))))
        action (next-run-action scheduler id task (first fires))]
    {:task   (assoc task :active-run (:run action))
     :action action}))

(defn- finish-run!
  [scheduler id token outcome error scheduled-at]
  (let [action* (atom nil)]
    (swap! (:tasks scheduler)
           (fn [tasks]
             (if-let [task (get tasks id)]
               (if (= token (get-in task [:active-run :token]))
                 (let [task (assoc task :active-run nil)
                       task (case outcome
                              :success (assoc task :consecutive-errors 0)
                              :error (update-after-error task scheduled-at error)
                              :interrupted task
                              task)]
                   (cond
                     (:disabled? task)
                     (dissoc tasks id)

                     (and (= :error outcome) (= :retry-with-backoff (on-error-mode task)))
                     (assoc tasks id task)

                     (and (not= :interrupted outcome) (seq (:pending-fire-ats task)))
                     (let [next-task   (assoc task :pending-fire-ats (vec (rest (:pending-fire-ats task))))
                           next-action (next-run-action scheduler id task (first (:pending-fire-ats task)))]
                       (reset! action* next-action)
                       (assoc tasks id (assoc next-task :active-run (:run next-action))))

                     (done? task)
                     (dissoc tasks id)

                     :else
                     (assoc tasks id task)))
                 tasks)
               tasks)))
    (when-let [action @action*]
      (begin-run! scheduler id (:run action)))))

(defn- timeout-run!
  [scheduler id token scheduled-at]
  (swap! (:tasks scheduler)
         (fn [tasks]
           (if-let [task (get tasks id)]
             (if (= token (get-in task [:active-run :token]))
               (do
                 (log/warn :scheduler/timeout :id (:id task) :scheduled-at (str scheduled-at))
                 (some-> (get-in task [:active-run :thread]) .interrupt)
                 (assoc tasks id (-> task
                                     (assoc :active-run nil)
                                     (assoc :pending-fire-ats []))))
               tasks)
             tasks))))

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
  (let [now            ((:clock scheduler))
        validated-task (schema/conform! task-schema task)
        task           (assoc validated-task
                         :created-at now
                         :next-fire-at (next-time (:trigger validated-task) now)
                         :remaining-fires (when (#{:delay :at} (get-in validated-task [:trigger :kind])) 1)
                         :pending-fire-ats []
                         :consecutive-errors 0
                         :active-run nil)]
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
      (let [{:keys [fires task]} (due-fires task now)]
        (when (seq fires)
          (let [action* (atom nil)]
            (swap! (:tasks scheduler)
                   (fn [tasks]
                     (if-let [current (get tasks id)]
                       (let [current (assoc current
                                       :next-fire-at (:next-fire-at task)
                                       :remaining-fires (:remaining-fires task))]
                         (cond
                           (:active-run current)
                           (assoc tasks id (enqueue-fires current fires))

                           :else
                           (let [{:keys [task action]} (plan-due-run scheduler id current fires)]
                             (reset! action* action)
                             (assoc tasks id task))))
                       tasks)))
            (when-let [action @action*]
              (begin-run! scheduler id (:run action))))))))
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
  (doseq [[_ task] @(:tasks scheduler)]
    (some-> (get-in task [:active-run :thread]) .interrupt))
  (reset! (:runner scheduler) nil)
  (reset! (:tasks scheduler) {})
  nil)
