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
      (should-contain :prefer-entity-files (:schema sut/root))))

  (describe "entity conformance"

    (it "conforms a defaults entity"
      (should= {:crew "main" :model "llama"}
               (sut/conform-entity :defaults {:crew :main :model :llama})))

    (it "coerces model aliases to strings on crew"
      (should= {:model "llama"}
               (sut/conform-entity :crew {:model :llama})))

    (it "rejects invalid provider field types"
      (let [result (sut/conform-entity :providers {:headers 42})]
        (should (schema/error? result))
        (should= {:headers "must be a map"}
                 (schema/message-map result)))))

  (describe "root schema validation"

    (it "rejects invalid inline provider values through the root schema"
      (let [result (schema/conform (:schema sut/root) {:providers {:openai {:headers 42}}})]
        (should (schema/error? result))
        (should= {:providers {:openai {:headers "must be a map"}}}
                 (schema/message-map result))))

    (it "prefer-entity-files defaults to false"
      (should= false (get-in sut/root [:schema :prefer-entity-files :default]))))

  (describe "map-of-id validation"

    (it "conforms a crew map by applying the crew entity schema to each value"
      (should= {"main" {:model "llama" :soul "You are Isaac."}}
               (sut/conform-entities! :crew {"main" {:model :llama :soul "You are Isaac."}})))

    (it "returns field-qualified errors for invalid entity values"
      (let [result (sut/conform-entities :models {"echo" {:context-window "wide" :model "echo" :provider :grover}})]
        (should (schema/error? result))
        (should= {"echo" {:context-window "can't coerce \"wide\" to int"}}
                 (schema/message-map result))))))
