(ns isaac.config.change-source-watch-spec
  (:require
    [isaac.config.change-source :as sut]
    [speclj.core :refer :all]))

(describe "watch service config change source"

  (it "publishes file changes from the watch service source"
    (if (System/getProperty "babashka.version")
      (should true)
      (let [home       (.toString (java.nio.file.Files/createTempDirectory "isaac-config-watch-"
                                                                          (make-array java.nio.file.attribute.FileAttribute 0)))
            config-dir (str home "/.isaac/config/crew")
            source     (sut/watch-service-source home)]
        (.mkdirs (java.io.File. config-dir))
        (sut/start! source)
        (Thread/sleep 50)
        (spit (str config-dir "/marvin.edn") "{:model :llama}")
        (let [deadline (+ (System/currentTimeMillis) 2000)
              result   (loop []
                         (if-let [value (sut/poll! source 100)]
                           value
                           (when (< (System/currentTimeMillis) deadline)
                             (recur))))]
          (should= "crew/marvin.edn" result))
        (sut/stop! source)))))
