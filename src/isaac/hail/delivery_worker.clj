(ns isaac.hail.delivery-worker
  (:require
    [clojure.edn :as edn]
    [clojure.pprint :as pprint]
    [isaac.charge :as charge]
    [isaac.config.api :as config]
    [isaac.drive.turn :as turn]
    [isaac.fs :as fs]
    [isaac.logger :as log]
    [isaac.nexus :as nexus]
    [isaac.scheduler :as scheduler]
    [isaac.session.store :as store]
    [isaac.tool.memory :as memory])
  (:import
    (java.time Instant)))

(def default-tick-ms 1000)

(def ^:private delays-ms
  {1 1000
   2 5000
   3 30000
   4 120000
   5 600000})

(defn- write-edn [value]
  (binding [*print-namespace-maps* false]
    (with-out-str (pprint/pprint value))))

(defn- runtime-state-dir [opts]
  (or (:state-dir opts)
      (config/state-dir)
      (nexus/get :state-dir)
      (throw (ex-info "hail delivery worker requires :state-dir" {}))))

(defn- filesystem []
  (or (fs/instance)
      (throw (ex-info "hail delivery worker requires :fs in system" {}))))

(defn- deliveries-dir [state-dir]
  (str state-dir "/hail/deliveries"))

(defn- inflight-dir [state-dir]
  (str state-dir "/hail/inflight"))

(defn- delivered-dir [state-dir]
  (str state-dir "/hail/delivered"))

(defn- failed-dir [state-dir]
  (str state-dir "/hail/failed"))

(defn- record-path [dir id]
  (str dir "/" id ".edn"))

(defn- temp-path [path]
  (str path ".tmp"))

(defn- normalize-id [value]
  (cond
    (keyword? value) (name value)
    (string? value)  value
    (nil? value)     nil
    :else            (str value)))

(defn- id-keyword [value]
  (some-> value normalize-id keyword))

(defn- read-record [path]
  (let [fs* (filesystem)]
    (when (fs/exists? fs* path)
      (edn/read-string (fs/slurp fs* path)))))

(defn- write-record! [path record]
  (let [fs*  (filesystem)
        temp (temp-path path)]
    (fs/mkdirs fs* (fs/parent path))
    (fs/spit fs* temp (write-edn record))
    (fs/move fs* temp path)))

(defn- delete-record! [path]
  (fs/delete (filesystem) path))

(defn- list-deliveries [state-dir]
  (let [fs* (filesystem)
        dir (deliveries-dir state-dir)]
    (if-let [children (fs/children fs* dir)]
      (->> children
           (map #(read-record (str dir "/" %)))
           (remove nil?)
           (sort-by :id)
           vec)
      [])))

(defn- due? [record now]
  (if-let [next-attempt-at (:next-attempt-at record)]
    (not (.isAfter (Instant/parse next-attempt-at) now))
    true))

(defn- crew-config [cfg crew-id]
  (or (get-in cfg [:crew crew-id])
      (get-in cfg [:crew (keyword crew-id)])))

(defn- crew-max-in-flight [cfg crew-id]
  (or (:max-in-flight (crew-config cfg crew-id)) 1))

(defn- crew-available? [cfg session-store crew-id]
  (< (store/in-flight-count session-store crew-id)
     (crew-max-in-flight cfg crew-id)))

(defn- session-available? [cfg session-store session-id]
  (when-let [session (store/get-session session-store session-id)]
    (let [crew-id (normalize-id (:crew session))]
      (and (not (store/in-flight? session-store session-id))
           (crew-available? cfg session-store crew-id)
           session))))

(defn- bind-candidate [delivery session]
  (-> delivery
      (assoc :crew (id-keyword (:crew session))
             :session (id-keyword (:id session)))
      (dissoc :candidates)))

(defn- runnable-delivery [cfg session-store delivery]
  (if-let [session-id (normalize-id (:session delivery))]
    (when (session-available? cfg session-store session-id)
      delivery)
    (some (fn [{:keys [session]}]
            (when-let [session-entry (session-available? cfg session-store (normalize-id session))]
              (bind-candidate delivery session-entry)))
          (:candidates delivery))))

(defn- inflight-path [state-dir id]
  (record-path (inflight-dir state-dir) id))

(defn- delivery-path [state-dir id]
  (record-path (deliveries-dir state-dir) id))

(defn- delivered-path [state-dir id]
  (record-path (delivered-dir state-dir) id))

(defn- failed-path [state-dir id]
  (record-path (failed-dir state-dir) id))

(defn- claim-delivery! [state-dir delivery]
  (write-record! (inflight-path state-dir (:id delivery)) delivery)
  (delete-record! (delivery-path state-dir (:id delivery)))
  delivery)

(defn- finish-delivered! [state-dir delivery]
  (write-record! (delivered-path state-dir (:id delivery)) delivery)
  (delete-record! (inflight-path state-dir (:id delivery))))

(defn- finish-failed! [state-dir delivery]
  (write-record! (failed-path state-dir (:id delivery)) delivery)
  (delete-record! (inflight-path state-dir (:id delivery))))

(defn- backoff-ms [attempts]
  (get delays-ms attempts))

(defn- reschedule! [state-dir now delivery]
  (let [attempts (inc (:attempts delivery 0))]
    (if-let [delay-ms (backoff-ms attempts)]
      (if (= attempts 5)
        (do
          (finish-failed! state-dir (assoc delivery :attempts attempts))
          (log/error :hail/dead-lettered :id (:id delivery) :reason :exhausted))
        (do
          (write-record! (delivery-path state-dir (:id delivery))
                         (assoc delivery
                                :attempts attempts
                                :next-attempt-at (str (.plusMillis now delay-ms))))
          (delete-record! (inflight-path state-dir (:id delivery)))))
      (do
        (finish-failed! state-dir (assoc delivery :attempts attempts))
        (log/error :hail/dead-lettered :id (:id delivery) :reason :exhausted)))))

(defn- delivery-charge [cfg delivery]
  (charge/build {:config      cfg
                 :session-key (normalize-id (:session delivery))
                 :input       (get-in delivery [:hail :prompt])
                 :origin      {:kind :hail :hail-id (normalize-id (get-in delivery [:hail :id]))}}))

(defn- run-delivery! [cfg delivery]
  (let [charge (delivery-charge cfg delivery)]
    (if (charge/unresolved? charge)
      {:error (:charge/reason charge)}
      (turn/run-turn! charge))))

(defn- launch-delivery! [opts delivery]
  (let [cfg           (:cfg opts)
        session-store (:session-store opts)
        state-dir     (runtime-state-dir opts)
        session-id    (normalize-id (:session delivery))
        run!          (nexus/bound-runtime-fn
                        (bound-fn []
                          (try
                            (let [result (run-delivery! cfg delivery)]
                              (if (:error result)
                                (reschedule! state-dir (:now opts) delivery)
                                (finish-delivered! state-dir delivery))
                              result)
                            (catch Exception e
                              (reschedule! state-dir (:now opts) delivery)
                              {:error :exception :message (.getMessage e)})
                            (finally
                              (store/clear-in-flight! session-store session-id)))))]
    (when (store/mark-in-flight! session-store session-id)
      (claim-delivery! state-dir delivery)
      (future (run!)))))

(defn tick!
  [{:keys [cfg session-store now] :as opts}]
  (let [cfg           (config/normalize-config (or cfg (config/snapshot) {}))
        state-dir     (runtime-state-dir opts)
        session-store (or session-store
                          (nexus/get-in [:sessions :store])
                          (store/create state-dir))
        now           (or now (memory/now))]
    (config/set-snapshot! cfg)
    (->> (list-deliveries state-dir)
         (filter #(due? % now))
         (map #(runnable-delivery cfg session-store %))
         (remove nil?)
         (map #(launch-delivery! (assoc opts
                                   :cfg cfg
                                   :now now
                                   :session-store session-store
                                   :state-dir state-dir)
                                 %))
         (remove nil?)
         vec)))

(defn start!
  [{:keys [tick-ms]
    :or   {tick-ms default-tick-ms}}]
  (let [shared-scheduler (or (nexus/get :scheduler)
                             (throw (ex-info "hail delivery worker requires :scheduler in isaac.nexus" {})))]
    (scheduler/schedule! shared-scheduler
                         {:id      :hail/deliver
                          :trigger {:kind :interval :ms tick-ms}
                          :handler (fn [_] (tick! {}))})
    {:scheduler shared-scheduler
     :task-id   :hail/deliver}))

(defn stop! [{:keys [scheduler task-id]}]
  (when scheduler
    (scheduler/cancel! scheduler task-id)))
