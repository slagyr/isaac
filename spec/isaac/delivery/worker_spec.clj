(ns isaac.delivery.worker-spec
  (:require
    [isaac.config.loader :as config]
    [isaac.comm.discord.rest :as discord-rest]
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

  (describe "send!"

    (it "returns ok true for a successful discord delivery"
      (with-redefs [config/load-config (fn [& _] {:comms {:discord {:token "tok" :message-cap 123}}})
                    discord-rest/post-message! (fn [request]
                                                 (should= {:channel-id "C999" :content "Hello" :message-cap 123 :token "tok"}
                                                          request)
                                                 {:status 204})]
        (should= {:ok true}
                 (sut/send! "/test/isaac" {:comm :discord :target "C999" :content "Hello"})))))

    (it "returns transient failure for a transient discord response"
      (with-redefs [config/load-config (fn [& _] {:comms {:discord {:token "tok"}}})
                    discord-rest/post-message! (fn [_] {:status 429})
                    discord-rest/transient-response? (fn [_] true)]
        (should= {:ok false :transient? true}
                 (sut/send! "/test/isaac" {:comm :discord :target "C999" :content "Hello"}))))

    (it "returns permanent failure for a non-transient discord response"
      (with-redefs [config/load-config (fn [& _] {:comms {:discord {:token "tok"}}})
                    discord-rest/post-message! (fn [_] {:status 403})
                    discord-rest/transient-response? (fn [_] false)]
        (should= {:ok false :transient? false}
                 (sut/send! "/test/isaac" {:comm :discord :target "C999" :content "Hello"}))))

    (it "returns permanent failure for an unknown comm"
      (should= {:ok false :transient? false}
               (sut/send! "/test/isaac" {:comm :pigeon :target "L1" :content "Hello"})))

  )

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
             (select-keys (last @log/captured-logs) [:event :id])))
