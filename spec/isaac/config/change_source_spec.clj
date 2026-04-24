(ns isaac.config.change-source-spec
  (:require
    [isaac.config.change-source :as sut]
    [speclj.core :refer :all]))

(describe "config change source"

  (it "publishes config-relative paths immediately for the memory source"
    (let [source (sut/memory-source "/tmp/isaac-home")]
      (sut/start! source)
      (sut/notify-path! source "/tmp/isaac-home/.isaac/config/crew/marvin.edn")
      (should= "crew/marvin.edn" (sut/poll! source 0))
      (sut/stop! source)))

  (it "ignores paths outside the config root for the memory source"
    (let [source (sut/memory-source "/tmp/isaac-home")]
      (sut/start! source)
      (sut/notify-path! source "/tmp/isaac-home/random.txt")
      (should= nil (sut/poll! source 0))
      (sut/stop! source)))

  (it "noop source poll blocks for the requested duration"
    (let [source  (sut/->NoopWatchServiceChangeSource "/tmp/home")
          start   (System/currentTimeMillis)
          result  (sut/poll! source 100)
          elapsed (- (System/currentTimeMillis) start)]
      (should= nil result)
      (should (>= elapsed 90)))))
