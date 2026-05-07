(ns isaac.delivery.worker
  (:require
    [isaac.comm.discord.rest :as discord-rest]
    [isaac.config.loader :as config]
    [isaac.delivery.backoff :as backoff]
    [isaac.delivery.queue :as queue]
    [isaac.logger :as log]
    [isaac.tool.memory :as memory])
  (:import
    (java.time Instant)))

(def default-tick-ms 10000)

(defn- discord-config [cfg]
  (merge (or (get-in cfg [:channels :discord]) {})
         (or (get-in cfg [:comms :discord]) {})))

(defn send! [state-dir record]
  (case (keyword (:comm record))
    :discord (let [cfg         (config/load-config {:home state-dir})
                   discord-cfg (discord-config cfg)
                   response    (discord-rest/post-message! {:channel-id  (:target record)
                                                            :content     (:content record)
                                                            :message-cap (:message-cap discord-cfg)
                                                            :token       (:token discord-cfg)})]
               (cond
                 (< (:status response 0) 400) {:ok true}
                 (discord-rest/transient-response? response) {:ok false :transient? true}
                 :else {:ok false :transient? false}))
    {:ok false :transient? false}))

(defn- due? [record now]
  (if-let [next-attempt-at (:next-attempt-at record)]
    (not (.isAfter (Instant/parse next-attempt-at) now))
    true))

(defn- reschedule! [state-dir now record]
  (let [attempts (inc (:attempts record 0))]
    (if-let [delay-ms (backoff/delay-ms attempts)]
      (if (= attempts 5)
        (do
          (queue/move-to-failed! state-dir (:id record) {:attempts attempts})
          (log/error :delivery/dead-lettered :id (:id record)))
        (queue/update-pending! state-dir (:id record) {:attempts        attempts
                                                       :next-attempt-at (str (.plusMillis now delay-ms))}))
      (do
        (queue/move-to-failed! state-dir (:id record) {:attempts attempts})
        (log/error :delivery/dead-lettered :id (:id record))))))

(defn- process-record! [state-dir now record]
  (when (due? record now)
    (let [result (try
                   (send! state-dir record)
                   (catch Exception e
                     {:error (.getMessage e) :ok false :transient? true}))]
      (if (:ok result)
        (queue/delete-pending! state-dir (:id record))
        (reschedule! state-dir now record)))))

(defn tick!
  [{:keys [now state-dir]}]
  (let [now (or now (memory/now))]
    (doseq [record (queue/list-pending state-dir)]
      (process-record! state-dir now record))))

(defn start!
  [{:keys [state-dir tick-ms]
    :or   {tick-ms default-tick-ms}}]
  (let [running? (atom true)
        runner   (future
                   (while @running?
                     (tick! {:state-dir state-dir})
                     (Thread/sleep tick-ms)))]
    {:running? running?
     :runner   runner}))

(defn stop! [{:keys [running? runner]}]
  (when running?
    (reset! running? false))
  (when runner
    (future-cancel runner)))
