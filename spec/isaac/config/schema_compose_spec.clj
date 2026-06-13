(ns isaac.config.schema-compose-spec
  (:require
    [isaac.config.schema-compose :as sut]
    [isaac.logger :as log]
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

    (it "a later module's table entry overrides an earlier one wholesale — no error, logged"
      (let [index {:mod.a {:manifest {:isaac.config/schema
                                      {:tools {:schema {:type   :map
                                                        :schema {:web_search {:type   :map
                                                                              :schema {:api-key {:type :string}}}}}}}}}
                   :mod.b {:manifest {:isaac.config/schema
                                      {:tools {:schema {:schema {:web_search {:type   :map
                                                                             :schema {:api-key {:type :int}}}}}}}}}}]
        (log/capture-logs
          (let [root (sut/compose-root-schema index)]
            ;; override is a feature: the later module's entry wins whole
            (should= :int (get-in root [:schema :tools :schema :web_search :schema :api-key :type]))
            (should (some #(= :tools/override (:event %)) @log/captured-logs))))))

    (it "errors when two modules disagree on a table's shell (structure, not entries)"
      (let [index {:mod.a {:manifest {:isaac.config/schema {:tools {:schema {:type :map :description "A"}}}}}
                   :mod.b {:manifest {:isaac.config/schema {:tools {:schema {:type :map :description "B"}}}}}}]
        (should-throw clojure.lang.ExceptionInfo (sut/compose-root-schema index))))

    (it "the more dependent module wins on override — topological order, not alphabetical"
      ;; mod.a depends on mod.z, so load order is z THEN a (a overrides z).
      ;; Alphabetical order would wrongly let z (later letter) win.
      (let [index {:mod.a {:manifest {:deps {:mod.z {}}
                                      :isaac.config/schema
                                      {:tools {:schema {:type :map
                                                        :schema {:web_search {:type :map :schema {:api-key {:type :string}}}}}}}}}
                   :mod.z {:manifest {:isaac.config/schema
                                      {:tools {:schema {:schema {:web_search {:type :map :schema {:api-key {:type :int}}}}}}}}}}
            root  (sut/compose-root-schema index)]
        (should= :string (get-in root [:schema :tools :schema :web_search :schema :api-key :type])))))

  (describe "descriptors"

    (it "includes entity metadata for crew cron and hooks"
      (let [descriptors (sut/descriptors (module-loader/builtin-index))]
        (should= "crew" (:entity-dir (:crew descriptors)))
        (should (true? (:frontmatter? (:crew descriptors))))
        (should= "cron" (:entity-dir (:cron descriptors)))
        (should= "hooks" (:entity-dir (:hooks descriptors)))))))