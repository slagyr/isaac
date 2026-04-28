(ns isaac.config.change-source-watch-spec
  (:require
    [isaac.config.change-source :as sut]
    [speclj.core :refer :all]))

(describe "editor-artifact?"

  (it "accepts normal config files"
    (should-not (sut/editor-artifact? "/home/user/.isaac/config/crew/marvin.edn"))
    (should-not (sut/editor-artifact? "/home/user/.isaac/config/models/llama.edn"))
    (should-not (sut/editor-artifact? "crew/marvin.edn")))

  (it "rejects vim swap files"
    (should (sut/editor-artifact? "/home/user/.isaac/config/crew/.marvin.edn.swp"))
    (should (sut/editor-artifact? "/home/user/.isaac/config/crew/.marvin.edn.swo"))
    (should (sut/editor-artifact? "/home/user/.isaac/config/crew/.marvin.edn.swx")))

  (it "rejects vim/nano backup files ending with ~"
    (should (sut/editor-artifact? "/home/user/.isaac/config/crew/marvin.edn~"))
    (should (sut/editor-artifact? "/home/user/.isaac/config/crew/main.edn~")))

  (it "rejects emacs lock files starting with .#"
    (should (sut/editor-artifact? "/home/user/.isaac/config/crew/.#marvin.edn"))
    (should (sut/editor-artifact? "/home/user/.isaac/config/models/.#llama.edn")))

  (it "rejects emacs autosave files wrapped in #"
    (should (sut/editor-artifact? "/home/user/.isaac/config/crew/#marvin.edn#"))
    (should (sut/editor-artifact? "/home/user/.isaac/config/#isaac.edn#")))

  (it "rejects bare numeric atomic-rename artifacts like 4913"
    (should (sut/editor-artifact? "/home/user/.isaac/config/4913"))
    (should (sut/editor-artifact? "/home/user/.isaac/config/12345")))

  (it "does not reject files whose names contain digits mixed with letters"
    (should-not (sut/editor-artifact? "/home/user/.isaac/config/crew/agent1.edn"))))

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
