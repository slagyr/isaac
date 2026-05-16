(ns isaac.cron.scheduler-spec
  (:require
    [isaac.bridge.core :as bridge]
    [isaac.comm.null :as null-comm]
    [isaac.config.loader :as config]
    [isaac.cron.scheduler :as sut]
    [isaac.cron.state :as cron-state]
    [isaac.fs :as fs]
    [isaac.logger :as log]
    [isaac.session.store :as store]
    [isaac.session.store.file :as file-store]
    [isaac.spec-helper :as helper]
    [isaac.system :as system]
    [speclj.core :refer :all])
  (:import
    (java.time ZonedDateTime)
    (java.time.format DateTimeFormatter)))

(def ^:private offset-formatter
  (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ssZ"))

(defn- zdt [s]
  (ZonedDateTime/parse s offset-formatter))

(describe "cron scheduler"

  (helper/with-captured-logs)

  (around [it]
    (system/with-system {:state-dir "/test/isaac"}
      (binding [fs/*fs* (fs/mem-fs)]
        (it))))

  (describe "CronModule lifecycle"

    (it "starts the scheduler on startup when cron jobs are present"
      (let [started (atom nil)
          module  (sut/make {:state-dir "/test/isaac"})]
        (with-redefs [sut/start! (fn [opts]
                                   (reset! started opts)
                                   ::runner)]
          ((requiring-resolve 'isaac.configurator/on-startup!) module {"health-check" {:expr "0 9 * * *"}})
          (should= {:cfg (or (config/snapshot) {}) :state-dir "/test/isaac"}
                   @started))))

    (it "stops the old scheduler and restarts it when the slice changes"
      (let [started (atom [])
            stopped (atom [])
            module  (sut/make {:state-dir "/test/isaac"})]
        (with-redefs [sut/start! (fn [opts]
                                   (swap! started conj opts)
                                   (keyword (str "runner-" (count @started))))
                      sut/stop!  (fn [runner]
                                   (swap! stopped conj runner))]
          ((requiring-resolve 'isaac.configurator/on-startup!) module {"alpha" {:expr "0 9 * * *"}})
          ((requiring-resolve 'isaac.configurator/on-config-change!) module
           {"alpha" {:expr "0 9 * * *"}}
           {"alpha" {:expr "0 10 * * *"}})
          (should= [:runner-1] @stopped)
          (should= 2 (count @started)))))

    (it "stops the scheduler when the slice is removed"
      (let [stopped (atom nil)
            module  (sut/make {:state-dir "/test/isaac"})]
        (with-redefs [sut/start! (fn [_] ::runner)
                      sut/stop!  (fn [runner]
                                   (reset! stopped runner))]
          ((requiring-resolve 'isaac.configurator/on-startup!) module {"alpha" {:expr "0 9 * * *"}})
          ((requiring-resolve 'isaac.configurator/on-config-change!) module
           {"alpha" {:expr "0 9 * * *"}}
           nil)
          (should= ::runner @stopped))))

    (it "leaves the scheduler alone when the slice is unchanged"
      (let [started (atom 0)
            stopped (atom 0)
            module  (sut/make {:state-dir "/test/isaac"})
            slice   {"alpha" {:expr "0 9 * * *"}}]
        (with-redefs [sut/start! (fn [_]
                                   (swap! started inc)
                                   ::runner)
                      sut/stop!  (fn [_]
                                   (swap! stopped inc))]
          ((requiring-resolve 'isaac.configurator/on-startup!) module slice)
          ((requiring-resolve 'isaac.configurator/on-config-change!) module slice slice)
          (should= 1 @started)
          (should= 0 @stopped)))))

  (it "fires due cron jobs through the normal turn flow"
    (let [calls      (atom [])
          store-stub (reify store/SessionStore
                       (open-session! [_ _ opts]
                         {:id   "session-1"
                          :crew (:crew opts)}))]
      (with-redefs [file-store/create-store (fn [& _] store-stub)
                    bridge/dispatch! (fn [request]
                                       (swap! calls conj {:session-key (:session-key request)
                                                          :input       (:input request)
                                                          :opts        (dissoc request :session-key :input)})
                                       {:ok true})]
        (sut/tick! {:cfg       {:tz      "America/Chicago"
                                :crew    {"main" {:soul "You are Isaac." :model "grover"}}
                                :models  {"grover" {:model "echo" :provider "grover" :context-window 32768}}
                                :providers {"grover" {}}
                                :cron    {"health-check" {:expr  "0 9 * * *"
                                                            :crew  "main"
                                                            :prompt "Run the health checkin."}}}
                    :now       (zdt "2026-04-21T09:00:00-0500")
                    :state-dir "/test/isaac"}))
        (let [actual (first @calls)]
          (should= "session-1" (:session-key actual))
          (should= "Run the health checkin." (:input actual))
          (let [opts (:opts actual)]
            (should= null-comm/channel (:comm opts))
            (should= {:kind :cron :name "health-check"} (:origin opts))
            (should= "main" (:crew-override opts))
            (should= "/test/isaac" (:home opts))
            (should= {:tz        "America/Chicago"
                      :crew      {"main" {:soul "You are Isaac." :model "grover"}}
                      :models    {"grover" {:model "echo" :provider "grover" :context-window 32768}}
                      :providers {"grover" {}}
                      :cron      {"health-check" {:expr  "0 9 * * *"
                                                   :crew  "main"
                                                   :prompt "Run the health checkin."}}}
                     (:cfg opts)))))
    (should= {"health-check" {:last-run    "2026-04-21T09:00:00-0500"
                               :last-status :succeeded
                               :last-error  nil}}
             (cron-state/read-state)))

  (it "logs and skips a missed cron window"
    (with-redefs [file-store/create-store (fn [& _]
                                            (reify store/SessionStore
                                              (open-session! [_ _ _]
                                                (throw (ex-info "should not create" {})))))
                  bridge/dispatch! (fn [& _]
                                     (throw (ex-info "should not run" {})))]
      (sut/tick! {:cfg       {:tz   "America/Chicago"
                              :cron {"health-check" {:expr  "0 9 * * *"
                                                      :crew  "main"
                                                      :prompt "Run the health checkin."}}}
                  :now       (zdt "2026-04-21T11:30:00-0500")
                  :state-dir "/test/isaac"}))
    (let [entry (first (filter #(= :cron/missed-schedule (:event %)) @log/captured-logs))]
      (should-not-be-nil entry)
      (should= "health-check" (:job entry)))
    (should= {} (cron-state/read-state)))

  (it "records failed job runs"
    (with-redefs [file-store/create-store (fn [& _]
                                            (reify store/SessionStore
                                              (open-session! [_ _ opts]
                                                {:id   "session-1"
                                                 :crew (:crew opts)})))
                  bridge/dispatch! (fn [& _]
                                     (throw (ex-info "boom" {})))]
      (sut/tick! {:cfg       {:tz      "America/Chicago"
                              :crew    {"main" {:soul "You are Isaac." :model "grover"}}
                              :models  {"grover" {:model "echo" :provider "grover"}}
                              :providers {"grover" {}}
                              :cron    {"health-check" {:expr  "0 9 * * *"
                                                          :crew  "main"
                                                          :prompt "Run the health checkin."}}}
                  :now       (zdt "2026-04-21T09:00:00-0500")
                  :state-dir "/test/isaac"}))
    (should= {"health-check" {:last-run    "2026-04-21T09:00:00-0500"
                                :last-status :failed
                                :last-error  "boom"}}
             (cron-state/read-state)))

  (it "creates cron sessions with a cron origin"
    (let [captured (atom nil)]
      (with-redefs [file-store/create-store (fn [& _]
                                              (reify store/SessionStore
                                                (open-session! [_ _ opts]
                                                  (reset! captured opts)
                                                  {:id "session-1" :crew (:crew opts)})))
                    bridge/dispatch! (fn [& _] {:ok true})]
        (sut/tick! {:cfg       {:tz      "America/Chicago"
                                :crew    {"main" {:soul "You are Isaac." :model "grover"}}
                                :models  {"grover" {:model "echo" :provider "grover" :context-window 32768}}
                                :providers {"grover" {}}
                                :cron    {"health-check" {:expr   "0 9 * * *"
                                                           :crew   "main"
                                                           :prompt "Run the health checkin."}}}
                    :now       (zdt "2026-04-21T09:00:00-0500")
                    :state-dir "/test/isaac"}))
      (should= {:kind :cron :name "health-check"} (:origin @captured)))))
