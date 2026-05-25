(ns isaac.cron.service-spec
  (:require
    [isaac.config.loader :as config]
    [isaac.config.configurator :as configurator]
    [isaac.cron.service :as sut]
    [isaac.fs :as fs]
    [isaac.scheduler :as scheduler-core]
    [isaac.spec-helper :as helper]
    [isaac.nexus :as nexus]
    [speclj.core :refer :all]))

(describe "cron scheduler"

  (helper/with-captured-logs)

  #_{:clj-kondo/ignore [:invalid-arity]}
  (around [it]
    (nexus/-with-nexus {:state-dir "/test/isaac" :fs (fs/mem-fs)}
      (it)))

  (describe "CronModule lifecycle"

    (it "starts the scheduler on startup when cron jobs are present"
      (let [started (atom nil)
          module  (sut/make {:state-dir "/test/isaac"})]
        (with-redefs [sut/start! (fn [opts]
                                   (reset! started opts)
                                   ::runner)]
          (configurator/on-startup! module {"health-check" {:expr "0 9 * * *"}})
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
          (configurator/on-startup! module {"alpha" {:expr "0 9 * * *"}})
          (configurator/on-config-change! module
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
          (configurator/on-startup! module {"alpha" {:expr "0 9 * * *"}})
          (configurator/on-config-change! module
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
          (configurator/on-startup! module slice)
           (configurator/on-config-change! module slice slice)
           (should= 1 @started)
           (should= 0 @stopped)))))

  (it "registers one shared-scheduler task per cron job"
    (let [scheduled (atom [])
          fake-scheduler {}
          cfg {:tz "America/Chicago"
               :cron {"nightly-cleanup" {:expr "0 3 * * *" :crew "main" :prompt "tidy up"}
                      "heartbeat"       {:expr "*/5 * * * *" :crew "main" :prompt "ping"}}}]
      (nexus/register! [:scheduler] fake-scheduler)
      (with-redefs [scheduler-core/schedule! (fn [scheduler task]
                                               (swap! scheduled conj [scheduler (select-keys task [:id :trigger])])
                                               task)]
        (sut/start! {:cfg cfg :state-dir "/test/isaac"}))
      (should= [[fake-scheduler {:id :cron/heartbeat :trigger {:kind :cron :expr "*/5 * * * *" :zone "America/Chicago"}}]
                [fake-scheduler {:id :cron/nightly-cleanup :trigger {:kind :cron :expr "0 3 * * *" :zone "America/Chicago"}}]]
               @scheduled)))

  (it "cancels registered cron tasks on stop"
    (let [cancelled (atom [])
          fake-scheduler {}]
      (with-redefs [scheduler-core/cancel! (fn [scheduler id]
                                             (swap! cancelled conj [scheduler id]))]
        (sut/stop! {:scheduler fake-scheduler :task-ids [:cron/nightly-cleanup :cron/heartbeat]}))
      (should= [[fake-scheduler :cron/nightly-cleanup]
                [fake-scheduler :cron/heartbeat]]
               @cancelled))))
