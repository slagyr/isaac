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
  )
