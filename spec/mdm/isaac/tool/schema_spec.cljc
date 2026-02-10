(ns mdm.isaac.tool.schema-spec
  (:require [c3kit.apron.schema :as schema]
            [mdm.isaac.tool.schema :as sut]
            [speclj.core :refer :all]))

(describe "Tool Schema"

  (context "tool definition schema"

    (it "validates a minimal valid tool"
      (let [tool {:name :my-tool
                  :description "A test tool"}
            result (schema/conform sut/tool tool)]
        (should-not (schema/error? result))
        (should= :my-tool (:name result))))

    (it "validates a full tool definition"
      (let [tool {:name :full-tool
                  :description "A complete tool"
                  :params {:content {:type :string :required true}}
                  :permissions #{:internal}}
            result (schema/conform sut/tool tool)]
        (should-not (schema/error? result))))

    (it "requires name"
      (let [tool {:description "Missing name"}
            result (schema/conform sut/tool tool)]
        (should (schema/error? result))))

    (it "requires description"
      (let [tool {:name :incomplete}
            result (schema/conform sut/tool tool)]
        (should (schema/error? result))))

    ;; Note: c3kit schema coerces types, so string "name" becomes keyword :name
    ;; These tests verify coercion works correctly
    (it "coerces string name to keyword"
      (let [tool {:name "my-tool" :description "Test tool"}
            result (schema/conform sut/tool tool)]
        (should-not (schema/error? result))
        (should= :my-tool (:name result))))

    (it "coerces keyword description to string"
      (let [tool {:name :tool :description :test-desc}
            result (schema/conform sut/tool tool)]
        (should-not (schema/error? result))
        (should= ":test-desc" (:description result)))))

  (context "param definition schema"

    (it "validates a basic param"
      (let [param {:type :string}
            result (schema/conform sut/param param)]
        (should-not (schema/error? result))))

    (it "validates param with required flag"
      (let [param {:type :string :required true}
            result (schema/conform sut/param param)]
        (should-not (schema/error? result))))

    ;; c3kit coerces "string" to :string
    (it "coerces string type to keyword"
      (let [param {:type "string"}
            result (schema/conform sut/param param)]
        (should-not (schema/error? result))
        (should= :string (:type result)))))

  (context "permissions"

    (it "accepts valid permission keywords"
      (let [tool {:name :tool
                  :description "Test"
                  :permissions #{:internal :read-only}}
            result (schema/conform sut/tool tool)]
        (should-not (schema/error? result))
        (should= #{:internal :read-only} (:permissions result))))

    (it "rejects invalid permission keywords"
      (let [tool {:name :tool
                  :description "Test"
                  :permissions #{:invalid-perm}}
            result (schema/conform sut/tool tool)]
        (should (schema/error? result))))))
