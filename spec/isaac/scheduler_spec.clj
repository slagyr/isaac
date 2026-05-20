(ns isaac.scheduler-spec
  (:require
    [isaac.scheduler :as sut]
    [speclj.core :refer :all])
  (:import
    (java.time Instant)))

(describe "scheduler"

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
      (should= 1 @fired*)
      (sut/tick! scheduler)
      (should= 1 @fired*)))

  (it "validates task shape before scheduling"
    (let [scheduler (sut/create {:clock (fn [] (Instant/parse "2026-05-20T10:00:00Z"))})
          error     (try
                      (sut/schedule! scheduler {:id :bad :trigger {:kind :interval :ms 100}})
                      (catch clojure.lang.ExceptionInfo e e))]
      (should= "task handler must be a function" (.getMessage error))))

  (it "validates trigger requirements before scheduling"
    (let [scheduler (sut/create {:clock (fn [] (Instant/parse "2026-05-20T10:00:00Z"))})
          error     (try
                      (sut/schedule! scheduler {:id :bad
                                                :trigger {:kind :at}
                                                :handler (fn [_] nil)})
                      (catch clojure.lang.ExceptionInfo e e))]
      (should= "at trigger requires :instant" (.getMessage error)))))
