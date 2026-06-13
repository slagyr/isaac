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

  (describe "merging contributions for the same config key"

    (it "deep-merges schema fragments contributed by two modules"
      (let [index {:mod.a {:manifest {:isaac.config/schema
                                      {:tools {:schema {:type   :map
                                                        :schema {:web_search {:type   :map
                                                                              :schema {:api-key {:type :string}}}}}}}}}
                   :mod.b {:manifest {:isaac.config/schema
                                      {:tools {:schema {:schema {:my_tool {:type   :map
                                                                           :schema {:flag {:type :boolean}}}}}}}}}}
            root  (sut/compose-root-schema index)]
        (should= {:type :string}  (get-in root [:schema :tools :schema :web_search :schema :api-key]))
        (should= {:type :boolean} (get-in root [:schema :tools :schema :my_tool :schema :flag]))))

    (it "errors when two modules define the same leaf differently"
      (let [index {:mod.a {:manifest {:isaac.config/schema
                                      {:tools {:schema {:type   :map
                                                        :schema {:web_search {:type   :map
                                                                              :schema {:api-key {:type :string}}}}}}}}}
                   :mod.b {:manifest {:isaac.config/schema
                                      {:tools {:schema {:schema {:web_search {:type   :map
                                                                             :schema {:api-key {:type :int}}}}}}}}}}]
        (should-throw clojure.lang.ExceptionInfo (sut/compose-root-schema index)))))

  (describe "descriptors"

    (it "includes entity metadata for crew cron and hooks"
      (let [descriptors (sut/descriptors (module-loader/builtin-index))]
        (should= "crew" (:entity-dir (:crew descriptors)))
        (should (true? (:frontmatter? (:crew descriptors))))
        (should= "cron" (:entity-dir (:cron descriptors)))
        (should= "hooks" (:entity-dir (:hooks descriptors)))))))