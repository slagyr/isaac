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

  (it "ignores non-config files under the config root for the memory source"
    (let [source (sut/memory-source "/tmp/isaac-home")]
      (sut/start! source)
      (sut/notify-path! source "/tmp/isaac-home/.isaac/config/.DS_Store")
      (sut/notify-path! source "/tmp/isaac-home/.isaac/config/isaac.edn.bak")
      (sut/notify-path! source "/tmp/isaac-home/.isaac/config/crew/marvin.tmp")
      (sut/notify-path! source "/tmp/isaac-home/.isaac/config/providers/openai.edn.orig")
      (should= nil (sut/poll! source 0))
      (sut/stop! source)))

  (it "publishes only allowlisted config shapes for the memory source"
    (let [source (sut/memory-source "/tmp/isaac-home")]
      (sut/start! source)
      (sut/notify-path! source "/tmp/isaac-home/.isaac/config/isaac.edn")
      (sut/notify-path! source "/tmp/isaac-home/.isaac/config/crew/marvin.md")
      (sut/notify-path! source "/tmp/isaac-home/.isaac/config/cron/nightly.md")
      (sut/notify-path! source "/tmp/isaac-home/.isaac/config/hooks/webhook.md")
      (sut/notify-path! source "/tmp/isaac-home/.isaac/config/models/grover.edn")
      (should= "isaac.edn" (sut/poll! source 0))
      (should= "crew/marvin.md" (sut/poll! source 0))
      (should= "cron/nightly.md" (sut/poll! source 0))
      (should= "hooks/webhook.md" (sut/poll! source 0))
      (should= "models/grover.edn" (sut/poll! source 0))
      (should= nil (sut/poll! source 0))
      (sut/stop! source)))

  (it "noop source poll blocks for the requested duration"
    (let [source  (sut/->NoopWatchServiceChangeSource "/tmp/home")
          start   (System/currentTimeMillis)
          result  (sut/poll! source 10)
          elapsed (- (System/currentTimeMillis) start)]
      (should= nil result)
      (should (>= elapsed 8)))))
