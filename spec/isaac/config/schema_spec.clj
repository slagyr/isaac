(ns isaac.config.schema-spec
  (:require
    [c3kit.apron.schema :as schema]
    [isaac.config.schema :as sut]
    [speclj.core :refer :all]))

(describe "config schema"

  (describe "entity schemas"

    (it "conforms a defaults entity"
      (should= {:crew "main" :model "llama"}
               (schema/conform! sut/defaults {:crew :main :model :llama})))

    (it "coerces model aliases to strings"
      (should= {:model "llama"}
               (schema/conform! sut/crew {:model :llama})))

    (it "rejects invalid provider field types"
      (let [result (schema/conform sut/provider {:headers 42})]
        (should (schema/error? result))
        (should= {:headers "must be a map"}
                 (schema/message-map result))))))

  (describe "map-of-id validation"

    (it "conforms a crew map by applying the crew entity schema to each value"
      (should= {"main" {:model "llama" :soul "You are Isaac."}}
               (sut/conform-entities! :crew {"main" {:model :llama :soul "You are Isaac."}})))

    (it "returns field-qualified errors for invalid entity values"
      (let [result (sut/conform-entities :models {"echo" {:context-window "wide" :model "echo" :provider :grover}})]
        (should (schema/error? result))
        (should= {"echo" {:context-window "can't coerce \"wide\" to int"}}
                 (schema/message-map result)))))
