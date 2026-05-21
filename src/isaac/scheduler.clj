(ns isaac.scheduler
  (:require
    [c3kit.apron.schema :as schema]
    [c3kit.apron.schema.refs :as refs]
    [isaac.cron.cron :as cron]
    [isaac.logger :as log])
  (:import
    (java.time Instant OffsetDateTime ZoneId ZonedDateTime)
    (java.util UUID)))

(refs/ensure-installed!)

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

(def trigger-schema
  {:name   :scheduler-trigger
   :type   :map
   :schema {:kind    {:type :keyword :validations [[:one-of :interval :delay :cron :at]]
                      :description "Trigger kind: :interval, :delay, :cron, or :at"}
            :ms      {:type :long
                      :validations [[:present-when? :kind :interval]
                                    [:present-when? :kind :delay]
                                    [:maybe? :pos?]]
                      :description "Relative delay or interval in milliseconds for :delay and :interval triggers"}
            :expr    {:type        :string
                      :validations [[:present-when? :kind :cron]]
                      :description "Cron expression for :cron triggers"}
            :zone    {:type :string :description "IANA time zone name used to evaluate :cron triggers"}
            :instant {:type        :ignore
                      :coerce      [parse-instant]
                      :validations [[:present-when? :kind :at]]
                      :description "Absolute instant for :at triggers; accepts java.time values or ISO-8601 strings"}}})

(def task-schema
  {:name   :scheduler-task
   :type   :map
   :schema {:id            {:type        :keyword :validate schema/present? :message "must be present"
                            :description "Stable task identifier used for registration and cancellation"}
            :trigger       {:type        :map :schema (:schema trigger-schema) :validate schema/present? :message "must be present"
                            :description "Scheduling trigger definition"}
            :handler       {:type :fn
                            :validations [schema/required]
                            :description "Function invoked when the task fires"}
            :coalesce      {:type :keyword
                            :validations [[:maybe? [:one-of :queue :skip]]]
                            :description "Overlap policy for due fires while a prior run is still active; supported values are :queue and :skip"}
            :on-error       {:type :keyword
                             :validations [[:maybe? [:one-of :log :retry]]]
                             :description "Handler failure policy; supported values are :log and :retry"}
            :backoff-ms     {:type :long
                             :validations [[:maybe? :pos?]]
                             :description "Initial retry delay in milliseconds when :on-error is :retry. Defaults to 1000."}
            :max-backoff-ms {:type :long
                             :validations [[:maybe? :pos?]]
                             :description "Cap on exponentially growing backoff in milliseconds. Defaults to 60000."}
            :retry-attempts {:type :long
                             :validations [[:maybe? :pos?]]
                             :description "Number of consecutive failures after which a :retry task is disabled. Defaults to 3."}
            :timeout-ms    {:type :long
                            :validations [[:maybe? :pos?]]
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

(defn- swap-with-action!
  "Like swap! but `f` returns [new-value action]. Returns the action from
   the CAS that won — never from a retried losing attempt. Lets transition
   logic stay pure: effects (logging, thread start, interrupt) run after
   the swap commits."
  [a f]
  (loop []
    (let [old @a
          [new action] (f old)]
      (if (compare-and-set! a old new)
        action
        (recur)))))

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

(defn- exponential-backoff-ms
  "Computes the next retry delay: min(max-ms, base-ms * 2^(n-1)), capped
   at 2^30 multiplier to stay well clear of long overflow."
  [base-ms max-ms consecutive-errors]
  (let [shift (max 0 (min 30 (dec consecutive-errors)))]
    (min max-ms (* base-ms (long (Math/pow 2 shift))))))

(defn- after-error
  "Pure. Returns [new-task notes] where notes is a map of side-effect data
   (log payloads, etc.) for the caller to act on after the swap commits."
  [task scheduled-at error]
  (let [consecutive-errors (inc (or (:consecutive-errors task) 0))
        task               (assoc task :consecutive-errors consecutive-errors)
        error-note         {:handler-error {:id           (:id task)
                                            :scheduled-at scheduled-at
                                            :error-msg    (.getMessage ^Exception error)}}]
    (case (on-error-mode task)
      :retry
      (if (>= consecutive-errors (:retry-attempts task))
        [(assoc task :pending-fire-ats [] :next-fire-at nil :disabled? true)
         (assoc error-note :disabled {:id (:id task) :attempts consecutive-errors})]
        (let [delay-ms (exponential-backoff-ms (:backoff-ms task)
                                                (:max-backoff-ms task)
                                                consecutive-errors)]
          [(-> task
               (assoc :pending-fire-ats [])
               (assoc :next-fire-at (.plusMillis scheduled-at delay-ms)))
           error-note]))

      [task error-note])))

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

(defn- compute-finish-transition
  "Pure. Returns [new-tasks notes]. Notes carry side-effect data:
   `:next-action`, `:handler-error`, `:disabled`."
  [scheduler id token outcome error scheduled-at tasks]
  (if-let [task (get tasks id)]
    (if (= token (get-in task [:active-run :token]))
      (let [task         (assoc task :active-run nil)
            [task notes] (case outcome
                           :success     [(assoc task :consecutive-errors 0) {}]
                           :error       (after-error task scheduled-at error)
                           :interrupted [task {}]
                           [task {}])]
        (cond
          (:disabled? task)
          [(dissoc tasks id) notes]

          (and (= :error outcome) (= :retry (on-error-mode task)))
          [(assoc tasks id task) notes]

          (and (not= :interrupted outcome) (seq (:pending-fire-ats task)))
          (let [next-task   (assoc task :pending-fire-ats (vec (rest (:pending-fire-ats task))))
                next-action (next-run-action scheduler id task (first (:pending-fire-ats task)))]
            [(assoc tasks id (assoc next-task :active-run (:run next-action)))
             (assoc notes :next-action next-action)])

          (done? task)
          [(dissoc tasks id) notes]

          :else
          [(assoc tasks id task) notes]))
      [tasks {}])
    [tasks {}]))

(defn- finish-run!
  [scheduler id token outcome error scheduled-at]
  (let [{:keys [next-action handler-error disabled]}
        (swap-with-action! (:tasks scheduler)
                           #(compute-finish-transition scheduler id token outcome error scheduled-at %))]
    (when-let [{:keys [id scheduled-at error-msg]} handler-error]
      (log/error :scheduler/handler-error :id id :scheduled-at (str scheduled-at) :error error-msg))
    (when-let [{:keys [id attempts]} disabled]
      (log/warn :scheduler/disabled :id id :reason :too-many-errors :attempts attempts))
    (when next-action
      (begin-run! scheduler id (:run next-action)))))

(defn- compute-timeout-transition
  "Pure. Returns [new-tasks notes] with `:thread-to-interrupt` and `:timed-out`."
  [id token scheduled-at tasks]
  (if-let [task (get tasks id)]
    (if (= token (get-in task [:active-run :token]))
      [(assoc tasks id (-> task (assoc :active-run nil) (assoc :pending-fire-ats [])))
       {:thread-to-interrupt (get-in task [:active-run :thread])
        :timed-out           {:id (:id task) :scheduled-at scheduled-at}}]
      [tasks {}])
    [tasks {}]))

(defn- timeout-run!
  [scheduler id token scheduled-at]
  (let [{:keys [thread-to-interrupt timed-out]}
        (swap-with-action! (:tasks scheduler)
                           #(compute-timeout-transition id token scheduled-at %))]
    (when-let [{:keys [id scheduled-at]} timed-out]
      (log/warn :scheduler/timeout :id id :scheduled-at (str scheduled-at)))
    (some-> thread-to-interrupt .interrupt)))

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

(def ^:private default-backoff-ms     1000)
(def ^:private default-max-backoff-ms 60000)
(def ^:private default-retry-attempts 3)

(defn- apply-retry-defaults [task]
  (if (= :retry (on-error-mode task))
    (cond-> task
      (nil? (:backoff-ms     task)) (assoc :backoff-ms     default-backoff-ms)
      (nil? (:max-backoff-ms task)) (assoc :max-backoff-ms default-max-backoff-ms)
      (nil? (:retry-attempts task)) (assoc :retry-attempts default-retry-attempts))
    task))

(defn schedule!
  "Registers a repeating or one-shot task.

   Task shape is validated against `task-schema`. Re-registering an existing
   `:id` throws. When `:on-error` is `:retry`, missing `:backoff-ms`,
   `:max-backoff-ms`, and `:retry-attempts` are filled with defaults
   (1000ms, 60000ms, 3 respectively)."
  [scheduler task]
  (let [now            ((:clock scheduler))
        validated-task (schema/conform! task-schema task)
        validated-task (apply-retry-defaults validated-task)
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

(defn- compute-tick-transition
  "Pure. Returns [new-tasks {:next-action ...}|{}]."
  [scheduler id task-now fires tasks]
  (if-let [current (get tasks id)]
    (let [current (assoc current
                    :next-fire-at (:next-fire-at task-now)
                    :remaining-fires (:remaining-fires task-now))]
      (if (:active-run current)
        [(assoc tasks id (enqueue-fires current fires)) {}]
        (let [{:keys [task action]} (plan-due-run scheduler id current fires)]
          [(assoc tasks id task) {:next-action action}])))
    [tasks {}]))

(defn tick!
  "Runs all tasks whose `:next-fire-at` is due according to the scheduler clock."
  [scheduler]
  (let [now ((:clock scheduler))]
    (doseq [[id task] @(:tasks scheduler)]
      (let [{:keys [fires task]} (due-fires task now)]
        (when (seq fires)
          (let [{:keys [next-action]}
                (swap-with-action! (:tasks scheduler)
                                   #(compute-tick-transition scheduler id task fires %))]
            (when next-action
              (begin-run! scheduler id (:run next-action))))))))
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
