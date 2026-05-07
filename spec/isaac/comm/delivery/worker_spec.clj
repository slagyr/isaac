(ns isaac.comm.delivery.worker-spec
  (:require
    [isaac.comm :as comm]
    [isaac.comm.delivery.queue :as queue]
    [isaac.comm.delivery.worker :as sut]
    [isaac.comm.registry :as comm-registry]
    [isaac.fs :as fs]
    [isaac.home :as home]
    [isaac.logger :as log]
    [isaac.spec-helper :as helper]
    [speclj.core :refer :all])
  (:import
    (java.time Instant)))

(deftype StubComm [result]
  comm/Comm
  (on-turn-start [_ _ _] nil)
  (on-text-chunk [_ _ _] nil)
  (on-tool-call [_ _ _] nil)
  (on-tool-cancel [_ _ _] nil)
  (on-tool-result [_ _ _ _] nil)
  (on-compaction-start [_ _ _] nil)
  (on-compaction-success [_ _ _] nil)
  (on-compaction-failure [_ _ _] nil)
  (on-compaction-disabled [_ _ _] nil)
  (on-turn-end [_ _ _] nil)
  (send! [_ _] result))

(describe "comm.delivery.worker"

  (helper/with-captured-logs)

  (around [it]
    (binding [fs/*fs*               (fs/mem-fs)
              home/*state-dir*      "/test/isaac"
              comm-registry/*registry* (atom (comm-registry/fresh-registry))]
      (it)))

  (describe "send!"

    (it "delegates to the registered comm instance"
      (comm-registry/register-instance! "stub" (->StubComm {:ok true}))
      (should= {:ok true}
               (sut/send! {:comm :stub :target "T1" :content "Hello"})))

    (it "returns permanent failure when no comm is registered for that key"
      (should= {:ok false :transient? false}
               (sut/send! {:comm :pigeon :target "L1" :content "Hello"}))))

  (it "deletes a pending delivery after a successful send"
    (queue/enqueue! {:id      "7f3a"
                     :comm    :stub
                     :target  "C999"
                     :content "Hello"})
    (comm-registry/register-instance! "stub" (->StubComm {:ok true}))
    (sut/tick! {:now (Instant/parse "2026-04-21T10:00:00Z")})
    (should-be-nil (queue/read-pending "7f3a")))

  (it "reschedules a transient failure with the next backoff"
    (queue/enqueue! {:id      "7f3a"
                     :comm    :stub
                     :target  "C999"
                     :content "Hello"})
    (comm-registry/register-instance! "stub" (->StubComm {:ok false :transient? true}))
    (sut/tick! {:now (Instant/parse "2026-04-21T10:00:00Z")})
    (should= {:attempts        1
              :next-attempt-at "2026-04-21T10:00:01Z"}
             (select-keys (queue/read-pending "7f3a") [:attempts :next-attempt-at])))

  (it "moves a delivery to failed and logs when it reaches max attempts"
    (queue/enqueue! {:id       "7f3a"
                     :comm     :stub
                     :target   "C999"
                     :content  "Hello"
                     :attempts 4})
    (comm-registry/register-instance! "stub" (->StubComm {:ok false :transient? true}))
    (sut/tick! {:now (Instant/parse "2026-04-21T10:00:00Z")})
    (should-be-nil (queue/read-pending "7f3a"))
    (should= 5 (:attempts (queue/read-failed "7f3a")))
    (should= {:event :delivery/dead-lettered :id "7f3a"}
             (select-keys (last @log/captured-logs) [:event :id]))))
