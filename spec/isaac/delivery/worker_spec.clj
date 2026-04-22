(ns isaac.delivery.worker-spec
  (:require
    [isaac.delivery.queue :as queue]
    [isaac.delivery.worker :as sut]
    [isaac.fs :as fs]
    [isaac.logger :as log]
    [isaac.spec-helper :as helper]
    [speclj.core :refer :all])
  (:import
    (java.time Instant)))

(describe "delivery worker"

  (helper/with-captured-logs)

  (around [it]
    (binding [fs/*fs* (fs/mem-fs)]
      (it)))

  (it "deletes a pending delivery after a successful send"
    (queue/enqueue! "/test/isaac" {:id      "7f3a"
                                    :comm    :discord
                                    :target  "C999"
                                    :content "Hello"})
    (with-redefs [sut/send! (fn [_state-dir record]
                              (should= :discord (:comm record))
                              {:ok true})]
      (sut/tick! {:state-dir "/test/isaac" :now (Instant/parse "2026-04-21T10:00:00Z")}))
    (should-be-nil (queue/read-pending "/test/isaac" "7f3a")))

  (it "reschedules a transient failure with the next backoff"
    (queue/enqueue! "/test/isaac" {:id      "7f3a"
                                    :comm    :discord
                                    :target  "C999"
                                    :content "Hello"})
    (with-redefs [sut/send! (fn [& _] {:ok false :transient? true})]
      (sut/tick! {:state-dir "/test/isaac" :now (Instant/parse "2026-04-21T10:00:00Z")}))
    (should= {:attempts        1
              :next-attempt-at "2026-04-21T10:00:01Z"}
             (select-keys (queue/read-pending "/test/isaac" "7f3a") [:attempts :next-attempt-at])))

  (it "moves a delivery to failed and logs when it reaches max attempts"
    (queue/enqueue! "/test/isaac" {:id       "7f3a"
                                    :comm     :discord
                                    :target   "C999"
                                    :content  "Hello"
                                    :attempts 4})
    (with-redefs [sut/send! (fn [& _] {:ok false :transient? true})]
      (sut/tick! {:state-dir "/test/isaac" :now (Instant/parse "2026-04-21T10:00:00Z")}))
    (should-be-nil (queue/read-pending "/test/isaac" "7f3a"))
    (should= 5 (:attempts (queue/read-failed "/test/isaac" "7f3a")))
    (should= {:event :delivery/dead-lettered :id "7f3a"}
             (select-keys (last @log/captured-logs) [:event :id]))))
