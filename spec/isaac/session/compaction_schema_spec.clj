(ns isaac.session.compaction-schema-spec
  (:require
    [c3kit.apron.schema :as schema]
    [isaac.session.compaction-schema :as sut]
    [speclj.core :refer [describe it should should=]]))

(describe "Compaction schema"

  (it "conforms a valid compaction config"
    (should= {:strategy :slinky :threshold 160 :head 80 :async? false}
             (schema/conform! sut/config-schema
                              {:strategy :slinky :threshold 160 :head 80 :async? false})))

  (it "does not add a cross-field error when head or threshold is missing"
    (let [result (schema/conform sut/config-schema {:threshold 160})]
      (should (schema/error? result))
      (should= {:head "is invalid"}
               (schema/message-map result))))

  (it "returns readable field errors for non-positive ints"
    (let [result (schema/conform sut/config-schema {:threshold -1 :head 80})]
      (should (schema/error? result))
      (should= {:threshold "must be positive"}
               (schema/message-map result))))

  (it "returns a readable entity error when head is not smaller than threshold"
    (let [result (schema/conform sut/config-schema {:threshold 80 :head 80})]
      (should (schema/error? result))
      (should= {:head-threshold "head must be smaller than threshold"}
               (schema/message-map result)))))
