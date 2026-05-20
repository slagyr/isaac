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
      (should= 1 @fired*))))
