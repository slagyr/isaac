(ns isaac.config.paths-spec
  (:require
    [isaac.config.paths :as sut]
    [isaac.marigold :as marigold]
    [speclj.core :refer :all]))

(describe "config paths"

  (it "root-filename is the root config filename"
    (should= "isaac.edn" sut/root-filename))

  (it "config-root joins home with .isaac/config"
    (should= "/home/me/.isaac/config" (sut/config-root "/home/me")))

  (it "config-path joins home with relative path under config root"
    (should= (str "/home/me/.isaac/config/crew/" marigold/first-mate ".edn")
             (sut/config-path "/home/me" (str "crew/" marigold/first-mate ".edn"))))

  (it "root-config-file points to isaac.edn under config root"
    (should= "/home/me/.isaac/config/isaac.edn"
             (sut/root-config-file "/home/me")))

  (it "entity-relative builds <kind>/<id>.edn from a keyword kind"
    (should= (str "crew/" marigold/first-mate ".edn") (sut/entity-relative :crew marigold/first-mate))
    (should= (str "models/" marigold/helm-mark-iii ".edn") (sut/entity-relative :models marigold/helm-mark-iii))
    (should= (str "providers/" marigold/helm-systems ".edn") (sut/entity-relative :providers marigold/helm-systems)))

  (it "soul-relative builds crew/<id>.md"
    (should= (str "crew/" marigold/first-mate ".md") (sut/soul-relative marigold/first-mate)))

  (it "cron-relative builds cron/<id>.md"
    (should= "cron/nightly.md" (sut/cron-relative "nightly")))

  (it "hook-relative builds hooks/<id>.md"
    (should= "hooks/webhook.md" (sut/hook-relative "webhook")))

  (it "config-file? allowlists known config file shapes"
    (should (sut/config-file? "isaac.edn"))
    (should (sut/config-file? (str "crew/" marigold/first-mate ".edn")))
    (should (sut/config-file? (str "models/" marigold/helm-mark-iii ".edn")))
    (should (sut/config-file? (str "providers/" marigold/helm-systems ".edn")))
    (should (sut/config-file? (str "crew/" marigold/first-mate ".md")))
    (should (sut/config-file? "cron/nightly.md"))
    (should (sut/config-file? "hooks/webhook.md")))

  (it "config-file? rejects unknown or malformed shapes"
    (should-not (sut/config-file? nil))
    (should-not (sut/config-file? ""))
    (should-not (sut/config-file? ".DS_Store"))
    (should-not (sut/config-file? "isaac.edn.bak"))
    (should-not (sut/config-file? "crew/.DS_Store"))
    (should-not (sut/config-file? (str "crew/" marigold/first-mate ".tmp")))
    (should-not (sut/config-file? (str "crew/" marigold/first-mate ".md.bak")))
    (should-not (sut/config-file? "notes/readme.txt"))
    (should-not (sut/config-file? (str "crew/nested/" marigold/first-mate ".edn")))))
