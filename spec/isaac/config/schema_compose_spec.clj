(ns isaac.config.schema-compose-spec
  (:require
    [isaac.config.schema-compose :as sut]
    [isaac.module.loader :as module-loader]
    [speclj.core :refer :all]))

(describe "config schema-compose"

  (describe "inline :schema contributions"

    (it "composes an inline schema map into the root schema"
      (let [index {:mod.x {:manifest {:isaac.config/schema
                                      {:tunes {:schema {:name        :tunes
                                                        :type        :map
                                                        :description "Shanty config"
                                                        :schema      {:volume {:type :int}}}}}}}}
            root  (sut/compose-root-schema index)]
        (should= {:type :int} (get-in root [:schema :tunes :schema :volume]))))

    (it "rejects a contribution whose :schema is not a map"
      (should-throw clojure.lang.ExceptionInfo
                    (sut/compose-root-schema
                      {:mod.x {:manifest {:isaac.config/schema
                                          {:tunes {:schema 42}}}}})))

    (it "meta-validates inline schemas and names the offender"
      (let [e (should-throw clojure.lang.ExceptionInfo
                            (sut/compose-root-schema
                              {:mod.x {:manifest {:isaac.config/schema
                                                  {:tunes {:schema {:type   :map
                                                                    :schema {:volume {:type :warble}}}}}}}}))]
        (should-contain ":warble" (.getMessage e)))))

  (describe "descriptors"

    (it "includes entity metadata for crew cron and hooks"
      (let [descriptors (sut/descriptors (module-loader/builtin-index))]
        (should= "crew" (:entity-dir (:crew descriptors)))
        (should (true? (:frontmatter? (:crew descriptors))))
        (should= "cron" (:entity-dir (:cron descriptors)))
        (should= "hooks" (:entity-dir (:hooks descriptors)))))))