(ns isaac.home-spec
  (:require
    [isaac.fs :as fs]
    [isaac.home :as sut]
    [isaac.logger :as log]
    [isaac.spec-helper :as helper]
    [speclj.core :refer :all]))

(describe "home"

  (helper/with-captured-logs)

  (around [it]
    (binding [fs/*fs* (fs/mem-fs)
              sut/*resolved-home* nil
              sut/*user-home* "/tmp/user"]
      (it)))

  (it "uses the explicit home before pointer files"
    (fs/mkdirs "/tmp/user/.config")
    (fs/spit "/tmp/user/.config/isaac.edn" "{:home \"/tmp/pointer\"}")
    (should= "/tmp/explicit" (sut/resolve-home "/tmp/explicit" nil)))

  (it "uses the fallback home before pointer files"
    (fs/mkdirs "/tmp/user/.config")
    (fs/spit "/tmp/user/.config/isaac.edn" "{:home \"/tmp/pointer\"}")
    (should= "/tmp/fallback" (sut/resolve-home nil "/tmp/fallback")))

  (it "reads the home pointer from ~/.config/isaac.edn"
    (fs/mkdirs "/tmp/user/.config")
    (fs/spit "/tmp/user/.config/isaac.edn" "{:home \"/tmp/pointer\"}")
    (should= "/tmp/pointer" (sut/resolve-home nil nil)))

  (it "falls back to ~/.isaac.edn when the XDG pointer is absent"
    (fs/spit "/tmp/user/.isaac.edn" "{:home \"/tmp/fallback-pointer\"}")
    (should= "/tmp/fallback-pointer" (sut/resolve-home nil nil)))

  (it "expands tildes in pointer values"
    (fs/mkdirs "/tmp/user/.config")
    (fs/spit "/tmp/user/.config/isaac.edn" "{:home \"~/.elsewhere\"}")
    (should= "/tmp/user/.elsewhere" (sut/resolve-home nil nil)))

  (it "expands relative homes against the current working directory"
    (let [cwd (System/getProperty "user.dir")]
      (should= (str cwd "/target/test-state")
               (sut/resolve-home "target/test-state" nil))))

  (it "logs a warning and falls through when a pointer file is malformed"
    (fs/mkdirs "/tmp/user/.config")
    (fs/spit "/tmp/user/.config/isaac.edn" "{:home")
    (should= "/tmp/user" (sut/resolve-home nil nil))
    (should= {:event :home/pointer-file-invalid :path "/tmp/user/.config/isaac.edn"}
             (select-keys (last @log/captured-logs) [:event :path]))))
