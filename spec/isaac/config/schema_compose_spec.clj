(ns isaac.config.schema-compose-spec
  (:require
    [isaac.config.schema-compose :as sut]
    [isaac.module.loader :as module-loader]
    [speclj.core :refer :all]))

(describe "config schema-compose"

  (describe "descriptors"

    (it "includes entity metadata for crew cron and hooks"
      (let [descriptors (sut/descriptors (module-loader/builtin-index))]
        (should= "crew" (:entity-dir (:crew descriptors)))
        (should (true? (:frontmatter? (:crew descriptors))))
        (should= "cron" (:entity-dir (:cron descriptors)))
        (should= "hooks" (:entity-dir (:hooks descriptors)))))))