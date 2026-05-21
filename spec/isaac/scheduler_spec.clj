(ns isaac.scheduler-spec
  (:require
    [isaac.logger :as log]
    [isaac.spec-helper :as helper]
    [isaac.scheduler :as sut]
    [speclj.core :refer :all])
  (:import
    (java.time Instant)))

(describe "scheduler"

  (helper/with-captured-logs)

  (it "lists scheduled tasks in registration order"
    (let [scheduler (sut/create {:clock (fn [] (Instant/parse "2026-05-20T10:00:00Z"))})]
      (sut/schedule! scheduler {:id :tick-a :trigger {:kind :interval :ms 100} :handler (fn [_] nil)})
      (sut/schedule! scheduler {:id :tick-b :trigger {:kind :delay :ms 200} :handler (fn [_] nil)})
      (should= [{:id :tick-a :trigger {:kind :interval :ms 100}}
                {:id :tick-b :trigger {:kind :delay :ms 200}}]
               (mapv #(select-keys % [:id :trigger]) (sut/list-tasks scheduler)))))

  (it "rejects re-registering an existing id"
    (let [scheduler (sut/create {:clock (fn [] (Instant/parse "2026-05-20T10:00:00Z"))})]
      (sut/schedule! scheduler {:id :tick :trigger {:kind :interval :ms 100} :handler (fn [_] nil)})
      (let [error (try
                    (sut/schedule! scheduler {:id :tick :trigger {:kind :interval :ms 200} :handler (fn [_] nil)})
                    (catch clojure.lang.ExceptionInfo e e))]
        (should= "task already scheduled: :tick" (.getMessage error)))))

  (it "cancels a scheduled task"
    (let [scheduler (sut/create {:clock (fn [] (Instant/parse "2026-05-20T10:00:00Z"))})]
      (sut/schedule! scheduler {:id :tick :trigger {:kind :interval :ms 100} :handler (fn [_] nil)})
      (sut/cancel! scheduler :tick)
      (should= [] (sut/list-tasks scheduler))))

  (it "fires interval tasks on each elapsed boundary"
    (let [now*      (atom (Instant/parse "2026-05-20T10:00:00Z"))
          fired*    (atom [])
          scheduler (sut/create {:clock (fn [] @now*)})]
      (sut/schedule! scheduler {:id :tick :trigger {:kind :interval :ms 100} :handler (fn [_] (swap! fired* conj :tick))})
      (reset! now* (Instant/parse "2026-05-20T10:00:00.350Z"))
      (sut/tick! scheduler)
      (helper/await-condition #(= 3 (count @fired*)))
      (should= [:tick :tick :tick] @fired*)))

  (it "fires delay tasks once"
    (let [now*      (atom (Instant/parse "2026-05-20T10:00:00Z"))
          fired*    (atom 0)
          scheduler (sut/create {:clock (fn [] @now*)})]
      (sut/schedule! scheduler {:id :retry :trigger {:kind :delay :ms 500} :handler (fn [_] (swap! fired* inc))})
      (reset! now* (Instant/parse "2026-05-20T10:00:00.499Z"))
      (sut/tick! scheduler)
      (should= 0 @fired*)
      (reset! now* (Instant/parse "2026-05-20T10:00:00.500Z"))
      (sut/tick! scheduler)
      (helper/await-condition #(= 1 @fired*))
      (should= 1 @fired*)
      (reset! now* (Instant/parse "2026-05-20T10:00:02Z"))
      (sut/tick! scheduler)
      (should= 1 @fired*)))

  (it "computes the next cron fire from the trigger zone"
    (let [now*      (atom (Instant/parse "2026-05-20T07:00:00Z"))
          fired*    (atom 0)
          scheduler (sut/create {:clock (fn [] @now*)})]
      (sut/schedule! scheduler {:id :nightly
                                :trigger {:kind :cron :expr "0 3 * * *" :zone "America/Chicago"}
                                :handler (fn [_] (swap! fired* inc))})
      (reset! now* (Instant/parse "2026-05-20T07:59:59Z"))
      (sut/tick! scheduler)
      (should= 0 @fired*)
      (reset! now* (Instant/parse "2026-05-20T08:00:00Z"))
      (sut/tick! scheduler)
      (helper/await-condition #(= 1 @fired*))
      (should= 1 @fired*)))

  (it "fires :at tasks once at the absolute instant"
    (let [now*      (atom (Instant/parse "2026-05-20T10:00:00Z"))
          fired*    (atom 0)
          scheduler (sut/create {:clock (fn [] @now*)})]
      (sut/schedule-once! scheduler {:id :alarm
                                     :trigger {:kind :at :instant "2026-05-20T10:00:30Z"}
                                     :handler (fn [_] (swap! fired* inc))})
      (reset! now* (Instant/parse "2026-05-20T10:00:29Z"))
      (sut/tick! scheduler)
      (should= 0 @fired*)
      (reset! now* (Instant/parse "2026-05-20T10:00:30Z"))
      (sut/tick! scheduler)
      (helper/await-condition #(= 1 @fired*))
      (should= 1 @fired*)
      (reset! now* (Instant/parse "2026-05-20T10:01:00Z"))
      (sut/tick! scheduler)
      (should= 1 @fired*)))

  (it "fires past :at tasks on the next tick only once"
    (let [now*      (atom (Instant/parse "2026-05-20T10:00:00Z"))
          fired*    (atom 0)
          scheduler (sut/create {:clock (fn [] @now*)})]
      (sut/schedule-once! scheduler {:id :late
                                      :trigger {:kind :at :instant "2026-05-20T09:00:00Z"}
                                      :handler (fn [_] (swap! fired* inc))})
      (sut/tick! scheduler)
      (helper/await-condition #(= 1 @fired*))
      (should= 1 @fired*)
      (sut/tick! scheduler)
      (should= 1 @fired*)))

  (it "validates task shape before scheduling"
    (let [scheduler (sut/create {:clock (fn [] (Instant/parse "2026-05-20T10:00:00Z"))})
          error     (try
                      (sut/schedule! scheduler {:id :bad :trigger {:kind :interval :ms 100}})
                      (catch clojure.lang.ExceptionInfo e e))]
      (should= "is required" (get-in (ex-data error) [:handler :message]))))

  (it "validates trigger requirements before scheduling"
    (let [scheduler (sut/create {:clock (fn [] (Instant/parse "2026-05-20T10:00:00Z"))})
           error     (try
                       (sut/schedule! scheduler {:id :bad
                                                 :trigger {:kind :at}
                                                 :handler (fn [_] nil)})
                       (catch clojure.lang.ExceptionInfo e e))]
      (should= "at trigger requires :instant" (get-in (ex-data error) [:trigger :at-instant :message]))))

  (it "queues overlapping fires sequentially when coalesce is :queue"
    (let [now*          (atom (Instant/parse "2026-05-20T10:00:00Z"))
          release-first (promise)
          started*      (atom 0)
          scheduler     (sut/create {:clock (fn [] @now*)})]
      (sut/schedule! scheduler {:id       :slow
                                :trigger  {:kind :interval :ms 100}
                                :coalesce :queue
                                :handler  (fn [_]
                                            (let [n (swap! started* inc)]
                                              (when (= 1 n)
                                                @release-first)))})
      (reset! now* (Instant/parse "2026-05-20T10:00:00.300Z"))
      (sut/tick! scheduler)
      (helper/await-condition #(= 1 @started*))
      (deliver release-first true)
      (helper/await-condition #(= 3 @started*))
      (should= 3 @started*)))

  (it "drops overlapping fires when coalesce is :skip"
    (let [now*          (atom (Instant/parse "2026-05-20T10:00:00Z"))
          release-first (promise)
          started*      (atom 0)
          scheduler     (sut/create {:clock (fn [] @now*)})]
      (sut/schedule! scheduler {:id       :slow
                                :trigger  {:kind :interval :ms 100}
                                :coalesce :skip
                                :handler  (fn [_]
                                            (let [n (swap! started* inc)]
                                              (when (= 1 n)
                                                @release-first)))})
      (reset! now* (Instant/parse "2026-05-20T10:00:00.300Z"))
      (sut/tick! scheduler)
      (helper/await-condition #(= 1 @started*))
      (deliver release-first true)
      (helper/await-condition #(nil? (:active-run (first (sut/list-tasks scheduler)))))
      (should= 1 @started*)))

  (it "logs handler errors and keeps scheduling by default"
    (let [now*      (atom (Instant/parse "2026-05-20T10:00:00Z"))
          fired*    (atom 0)
          scheduler (sut/create {:clock (fn [] @now*)})]
      (sut/schedule! scheduler {:id      :flaky
                                :trigger {:kind :interval :ms 100}
                                :handler (fn [_]
                                           (swap! fired* inc)
                                           (throw (ex-info "boom" {})))})
      (reset! now* (Instant/parse "2026-05-20T10:00:00.300Z"))
      (sut/tick! scheduler)
      (helper/await-condition #(= 3 @fired*))
      (helper/await-condition #(= 3 (count (filter (fn [entry] (= :scheduler/handler-error (:event entry))) @log/captured-logs))))
      (should= 3 @fired*)
      (should= 3 (count (filter (fn [entry] (= :scheduler/handler-error (:event entry))) @log/captured-logs)))))

  (it "retries with backoff after a handler error"
    (let [now*      (atom (Instant/parse "2026-05-20T10:00:00Z"))
          fired*    (atom 0)
          scheduler (sut/create {:clock (fn [] @now*)})]
      (sut/schedule! scheduler {:id         :retry
                                :trigger    {:kind :interval :ms 100}
                                :on-error   :retry-with-backoff
                                :backoff-ms 500
                                :handler    (fn [_]
                                              (swap! fired* inc)
                                              (throw (ex-info "boom" {})))})
      (reset! now* (Instant/parse "2026-05-20T10:00:00.100Z"))
      (sut/tick! scheduler)
      (helper/await-condition #(= 1 @fired*))
      (reset! now* (Instant/parse "2026-05-20T10:00:00.599Z"))
      (sut/tick! scheduler)
      (should= 1 @fired*)
      (reset! now* (Instant/parse "2026-05-20T10:00:00.600Z"))
      (sut/tick! scheduler)
      (helper/await-condition #(= 2 @fired*))
      (should= 2 @fired*)))

  (it "disables a task after N consecutive handler errors"
    (let [now*      (atom (Instant/parse "2026-05-20T10:00:00Z"))
          fired*    (atom 0)
          scheduler (sut/create {:clock (fn [] @now*)})]
      (sut/schedule! scheduler {:id            :flaky
                                :trigger       {:kind :interval :ms 100}
                                :on-error      :disable-after-N
                                :disable-after 3
                                :handler       (fn [_]
                                                 (swap! fired* inc)
                                                 (throw (ex-info "boom" {})))})
      (reset! now* (Instant/parse "2026-05-20T10:00:01Z"))
      (sut/tick! scheduler)
      (helper/await-condition #(= 3 @fired*))
      (helper/await-condition #(empty? (sut/list-tasks scheduler)))
      (should= 3 @fired*)
      (should= [{:level :warn :event :scheduler/disabled :id :flaky :reason :too-many-errors}]
               (mapv #(select-keys % [:level :event :id :reason])
                     (filter (fn [entry] (= :scheduler/disabled (:event entry))) @log/captured-logs)))))

  (it "times out hung handlers"
    (let [now*      (atom (Instant/parse "2026-05-20T10:00:00Z"))
          release*  (promise)
          started*  (atom 0)
          scheduler (sut/create {:clock (fn [] @now*)})]
      (sut/schedule! scheduler {:id         :hang
                                :trigger    {:kind :interval :ms 100}
                                :timeout-ms 50
                                :handler    (fn [_]
                                              (swap! started* inc)
                                              @release*)})
      (reset! now* (Instant/parse "2026-05-20T10:00:00.300Z"))
      (sut/tick! scheduler)
      (helper/await-condition #(= 1 @started*))
      (helper/await-condition #(= 1 (count (filter (fn [entry] (= :scheduler/timeout (:event entry))) @log/captured-logs))))
      (deliver release* true)
      (should= 1 @started*)))

  (it "does not let a hung handler block other tasks"
    (let [now*      (atom (Instant/parse "2026-05-20T10:00:00Z"))
          release*  (promise)
          fast*     (atom 0)
          scheduler (sut/create {:clock (fn [] @now*)})]
      (sut/schedule! scheduler {:id      :slow
                                :trigger {:kind :interval :ms 100}
                                :handler (fn [_] @release*)})
      (sut/schedule! scheduler {:id      :fast
                                :trigger {:kind :interval :ms 100}
                                :handler (fn [_] (swap! fast* inc))})
      (reset! now* (Instant/parse "2026-05-20T10:00:00.300Z"))
      (sut/tick! scheduler)
      (helper/await-condition #(= 3 @fast*))
      (deliver release* true)
      (should= 3 @fast*))))
