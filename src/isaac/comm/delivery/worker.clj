(ns isaac.comm.delivery.worker
  (:require
    [isaac.comm :as comm]
    [isaac.comm.delivery.queue :as queue]
    [isaac.comm.registry :as comm-registry]
    [isaac.logger :as log]
    [isaac.tool.memory :as memory])
  (:import
    (java.time Instant)))

(def default-tick-ms 10000)

(def ^:private delays-ms
  {1 1000
   2 5000
   3 30000
   4 120000
   5 600000})

(defn- backoff-ms [attempts]
  (get delays-ms attempts))

(defn send! [record]
  (if-let [comm-inst (comm-registry/comm-for (:comm record))]
    (comm/send! comm-inst record)
    {:ok false :transient? false}))

(defn- due? [record now]
  (if-let [next-attempt-at (:next-attempt-at record)]
    (not (.isAfter (Instant/parse next-attempt-at) now))
    true))

(defn- reschedule! [now record]
  (let [attempts (inc (:attempts record 0))]
    (if-let [delay-ms (backoff-ms attempts)]
      (if (= attempts 5)
        (do
          (queue/move-to-failed! (:id record) {:attempts attempts})
          (log/error :delivery/dead-lettered :id (:id record)))
        (queue/update-pending! (:id record) {:attempts        attempts
                                             :next-attempt-at (str (.plusMillis now delay-ms))}))
      (do
        (queue/move-to-failed! (:id record) {:attempts attempts})
        (log/error :delivery/dead-lettered :id (:id record))))))

(defn- process-record! [now record]
  (when (due? record now)
    (let [result (try
                   (send! record)
                   (catch Exception e
                     {:error (.getMessage e) :ok false :transient? true}))]
      (if (:ok result)
        (queue/delete-pending! (:id record))
        (reschedule! now record)))))

(defn tick!
  [{:keys [now]}]
  (let [now (or now (memory/now))]
    (doseq [record (queue/list-pending)]
      (process-record! now record))))

(defn start!
  [{:keys [tick-ms]
    :or   {tick-ms default-tick-ms}}]
  (let [running? (atom true)
        runner   (future
                   ((bound-fn []
                      (while @running?
                        (tick! {})
                        (Thread/sleep tick-ms)))))]
    {:running? running?
     :runner   runner}))

(defn stop! [{:keys [running? runner]}]
  (when running?
    (reset! running? false))
  (when runner
    (future-cancel runner)))
