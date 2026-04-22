(ns isaac.cron.state-spec
  (:require
    [isaac.cron.state :as sut]
    [isaac.fs :as fs]
    [speclj.core :refer :all]))

(describe "cron state"

  (around [it]
    (binding [fs/*fs* (fs/mem-fs)]
      (it)))

  (it "returns an empty state map when the file does not exist"
    (should= {} (sut/read-state "/test/isaac")))

  (it "writes job status to cron.edn"
    (sut/write-job-state! "/test/isaac" "health-check" {:last-run    "2026-04-21T09:00:00-0500"
                                                           :last-status :succeeded})
    (should= {"health-check" {:last-run    "2026-04-21T09:00:00-0500"
                               :last-status :succeeded}}
             (sut/read-state "/test/isaac")))

  (it "merges updates into existing job state"
    (sut/write-job-state! "/test/isaac" "health-check" {:last-run    "2026-04-21T09:00:00-0500"
                                                           :last-status :failed
                                                           :last-error  "boom"})
    (sut/write-job-state! "/test/isaac" "health-check" {:last-status :succeeded
                                                           :last-error  nil})
    (should= {"health-check" {:last-run    "2026-04-21T09:00:00-0500"
                               :last-status :succeeded
                               :last-error  nil}}
             (sut/read-state "/test/isaac"))))
