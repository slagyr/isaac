(ns isaac.config.change-source-watch-spec
  (:require
    [isaac.config.change-source :as sut]
    [isaac.spec-helper :as helper]
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

  (it "notify-path ignores non-config files under the config root"
    (let [source (sut/watch-service-source "/tmp/isaac-home")]
      (sut/notify-path! source "/tmp/isaac-home/.isaac/config/.DS_Store")
      (sut/notify-path! source "/tmp/isaac-home/.isaac/config/isaac.edn.bak")
      (sut/notify-path! source "/tmp/isaac-home/.isaac/config/crew/marvin.tmp")
      (should= nil (sut/poll! source 0))))

  (it "notify-path publishes config-relative changes for the watch service source"
    (let [source (sut/watch-service-source "/tmp/isaac-home")]
      (sut/notify-path! source "/tmp/isaac-home/.isaac/config/crew/marvin.edn")
      (should= "crew/marvin.edn" (sut/poll! source 0)))))
