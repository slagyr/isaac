(ns isaac.config.schema-spec
  (:require
    [c3kit.apron.schema :as schema]
    [isaac.config.schema :as sut]
    [speclj.core :refer :all]))

(describe "config schema"

  (describe "entity specs are wrapped"

    (it "defaults is a named map spec"
      (should= :map (:type sut/defaults))
      (should= :defaults (:name sut/defaults))
      (should-contain :crew (:schema sut/defaults))
      (should-contain :model (:schema sut/defaults)))

    (it "crew is a named map spec"
      (should= :map (:type sut/crew))
      (should= :crew (:name sut/crew))
      (should-contain :model (:schema sut/crew))
      (should-contain :soul (:schema sut/crew)))

    (it "model is a named map spec"
      (should= :map (:type sut/model))
      (should= :model (:name sut/model))
      (should-contain :provider (:schema sut/model))
      (should-contain :context-window (:schema sut/model)))

    (it "provider is a named map spec"
      (should= :map (:type sut/provider))
      (should= :provider (:name sut/provider))
      (should-contain :api-key (:schema sut/provider))
      (should-contain :base-url (:schema sut/provider)))

    (it "root is a named map spec"
      (should= :map (:type sut/root))
      (should= :root (:name sut/root))
      (should-contain :crew (:schema sut/root))
      (should-contain :providers (:schema sut/root))
      (should-contain :prefer-entity-files (:schema sut/root)))

    (it "tools is a named map spec"
      (should= :map (:type sut/tools))
      (should= :tools (:name sut/tools))
      (should-contain :allow (:schema sut/tools))
      (should-contain :directories (:schema sut/tools)))

    (it "crew :tools field references the tools spec"
      (should= sut/tools (get-in sut/crew [:schema :tools]))))

  (describe "tools entity conformance"

    (it "conforms :allow as a seq of keywords"
      (should= {:allow [:read :write]}
               (schema/conform sut/tools {:allow [:read :write]})))

    (it "rejects :allow entries that cannot coerce to a keyword"
      (let [result (schema/conform sut/tools {:allow [{:not :keyword}]})]
        (should (schema/error? result))))

    (it "conforms :directories containing :cwd"
      (should= {:directories [:cwd]}
               (schema/conform sut/tools {:directories [:cwd]})))

    (it "conforms :directories containing absolute path strings"
      (should= {:directories ["/tmp/playground"]}
               (schema/conform sut/tools {:directories ["/tmp/playground"]})))

    (it "conforms :directories mixing :cwd and strings"
      (should= {:directories [:cwd "/tmp/playground"]}
               (schema/conform sut/tools {:directories [:cwd "/tmp/playground"]})))

    (it "rejects :directories with a non-:cwd keyword"
      (let [result (schema/conform sut/tools {:directories [:not-cwd]})]
        (should (schema/error? result))))

    (it "rejects :directories with a non-keyword non-string"
      (let [result (schema/conform sut/tools {:directories [42]})]
        (should (schema/error? result)))))

  (describe "entity conformance"

    (it "conforms a defaults entity"
      (should= {:crew "main" :model "llama"}
               (schema/conform sut/defaults {:crew :main :model :llama})))

    (it "coerces model aliases to strings on crew"
      (should= {:model "llama"}
               (schema/conform sut/crew {:model :llama})))

    (it "rejects invalid provider field types"
      (let [result (schema/conform sut/provider {:headers 42})]
        (should (schema/error? result))
        (should= {:headers "must be a map"}
                 (schema/message-map result)))))

  (describe "root schema validation"

    (it "rejects invalid inline provider values through the root schema"
      (let [result (schema/conform sut/root {:providers {:openai {:headers 42}}})]
        (should (schema/error? result))
        (should= {:providers {:openai {:headers "must be a map"}}}
                 (schema/message-map result))))

    (it "prefer-entity-files defaults to false"
      (should= false (get-in sut/root [:schema :prefer-entity-files :default])))

    (it "conforms a map-of-id crew section via :value-spec"
      (should= {:crew {"main" {:model "llama" :soul "You are Isaac."}}}
               (schema/conform sut/root
                               {:crew {"main" {:model :llama :soul "You are Isaac."}}})))

    (it "reports field errors inside a map-of-id section"
      (let [result (schema/conform sut/root
                                   {:models {"echo" {:context-window "wide"
                                                     :model          "echo"
                                                     :provider       :grover}}})]
        (should (schema/error? result))
        (should= {:models {"echo" {:context-window "can't coerce \"wide\" to int"}}}
                 (schema/message-map result))))))
