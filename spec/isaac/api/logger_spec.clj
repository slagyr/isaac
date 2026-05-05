(ns isaac.api.logger-spec
  (:require
    [isaac.api.logger :as sut]
    [isaac.logger :as impl]
    [speclj.core :refer :all]))

(describe "isaac.api.logger"

  (it "info logs an entry at :info level"
    (impl/capture-logs
      (sut/info :test-event :k "v")
      (should= 1 (count @impl/captured-logs))
      (should= :info (:level (first @impl/captured-logs)))
      (should= :test-event (:event (first @impl/captured-logs)))))

  (it "error logs an entry at :error level"
    (impl/capture-logs
      (sut/error :test-event :k "v")
      (should= :error (:level (first @impl/captured-logs)))))

  (it "warn logs an entry at :warn level"
    (impl/capture-logs
      (sut/warn :test-event)
      (should= :warn (:level (first @impl/captured-logs)))))

  (it "debug logs an entry at :debug level"
    (impl/capture-logs
      (sut/debug :test-event)
      (should= :debug (:level (first @impl/captured-logs)))))

  (it "log dispatches to the given level"
    (impl/capture-logs
      (sut/log :error :test-event)
      (should= :error (:level (first @impl/captured-logs)))))

  (it "ex logs exception context at :error level"
    (impl/capture-logs
      (sut/ex :test-event (Exception. "boom"))
      (let [entry (first @impl/captured-logs)]
        (should= :error (:level entry))
        (should= :test-event (:event entry))
        (should= "boom" (:error-message entry))))))
