(ns isaac.config.paths-spec
  (:require
    [isaac.config.paths :as sut]
    [speclj.core :refer :all]))

(describe "config paths"

  (it "root-filename is the root config filename"
    (should= "isaac.edn" sut/root-filename))

  (it "config-root joins home with .isaac/config"
    (should= "/home/me/.isaac/config" (sut/config-root "/home/me")))

  (it "config-path joins home with relative path under config root"
    (should= "/home/me/.isaac/config/crew/marvin.edn"
             (sut/config-path "/home/me" "crew/marvin.edn")))

  (it "root-config-file points to isaac.edn under config root"
    (should= "/home/me/.isaac/config/isaac.edn"
             (sut/root-config-file "/home/me")))

  (it "entity-relative builds <kind>/<id>.edn from a keyword kind"
    (should= "crew/marvin.edn"     (sut/entity-relative :crew "marvin"))
    (should= "models/gpt.edn"      (sut/entity-relative :models "gpt"))
    (should= "providers/anthropic.edn" (sut/entity-relative :providers "anthropic")))

  (it "soul-relative builds crew/<id>.md"
    (should= "crew/marvin.md" (sut/soul-relative "marvin")))

  (it "cron-relative builds cron/<id>.md"
    (should= "cron/nightly.md" (sut/cron-relative "nightly")))

  (it "hook-relative builds hooks/<id>.md"
    (should= "hooks/webhook.md" (sut/hook-relative "webhook")))

  (it "config-file? allowlists known config file shapes"
    (should (sut/config-file? "isaac.edn"))
    (should (sut/config-file? "crew/marvin.edn"))
    (should (sut/config-file? "models/gpt.edn"))
    (should (sut/config-file? "providers/openai.edn"))
    (should (sut/config-file? "crew/marvin.md"))
    (should (sut/config-file? "cron/nightly.md"))
    (should (sut/config-file? "hooks/webhook.md")))

  (it "config-file? rejects unknown or malformed shapes"
    (should-not (sut/config-file? nil))
    (should-not (sut/config-file? ""))
    (should-not (sut/config-file? ".DS_Store"))
    (should-not (sut/config-file? "isaac.edn.bak"))
    (should-not (sut/config-file? "crew/.DS_Store"))
    (should-not (sut/config-file? "crew/marvin.tmp"))
    (should-not (sut/config-file? "crew/marvin.md.bak"))
    (should-not (sut/config-file? "notes/readme.txt"))
    (should-not (sut/config-file? "crew/nested/marvin.edn"))))
