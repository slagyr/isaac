(ns isaac.cron.state-spec
  (:require
    [isaac.cron.state :as sut]
    [isaac.fs :as fs]
    [isaac.system :as system]
    [speclj.core :refer :all]))

(describe "cron state"

  (around [it]
    (system/with-system {:state-dir "/test/isaac"}
      (binding [fs/*fs* (fs/mem-fs)]
        (it))))

  (it "returns an empty state map when the file does not exist"
    (should= {} (sut/read-state)))

  (it "writes job status to cron.edn"
    (sut/write-job-state! "health-check" {:last-run    "2026-04-21T09:00:00-0500"
                                          :last-status :succeeded})
    (should= {"health-check" {:last-run    "2026-04-21T09:00:00-0500"
                               :last-status :succeeded}}
             (sut/read-state)))

  (it "merges updates into existing job state"
    (sut/write-job-state! "health-check" {:last-run    "2026-04-21T09:00:00-0500"
                                          :last-status :failed
                                          :last-error  "boom"})
    (sut/write-job-state! "health-check" {:last-status :succeeded
                                          :last-error  nil})
    (should= {"health-check" {:last-run    "2026-04-21T09:00:00-0500"
                               :last-status :succeeded
                               :last-error  nil}}
             (sut/read-state))))
