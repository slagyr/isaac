(ns mdm.isaac.tool.internal.config-spec
  (:require [c3kit.bucket.api :as db]
            [c3kit.bucket.spec-helperc :as helper]
            [mdm.isaac.setting.schema :as schema.setting]
            [mdm.isaac.tool.internal.config :as sut]
            [speclj.core :refer :all]))

(describe "tune-config tool"

  (helper/with-schemas [schema.setting/config])

  (context "get-config-tool"

    (it "returns nil when setting doesn't exist"
      (let [result ((:execute sut/get-config-tool) {:key :nonexistent})]
        (should= :ok (:status result))
        (should-be-nil (:value result))))

    (it "returns the setting value when it exists"
      (db/tx {:kind :config :key :think-delay-ms :value "5000"})
      (let [result ((:execute sut/get-config-tool) {:key :think-delay-ms})]
        (should= :ok (:status result))
        (should= "5000" (:value result))))

    (it "requires key parameter"
      (let [result ((:execute sut/get-config-tool) {})]
        (should= :error (:status result))
        (should-contain "key" (:message result)))))

  (context "set-config-tool"

    (it "creates a new setting"
      (let [result ((:execute sut/set-config-tool) {:key :my-setting :value "test-value"})]
        (should= :ok (:status result))
        (should= "test-value" (:value result))
        ;; Verify it was persisted
        (should= "test-value" (:value (db/ffind-by :config :key :my-setting)))))

    (it "updates an existing setting"
      (db/tx {:kind :config :key :existing-setting :value "old-value"})
      (let [result ((:execute sut/set-config-tool) {:key :existing-setting :value "new-value"})]
        (should= :ok (:status result))
        (should= "new-value" (:value result))
        ;; Verify only one record exists
        (should= 1 (count (db/find-by :config :key :existing-setting)))))

    (it "requires key parameter"
      (let [result ((:execute sut/set-config-tool) {:value "test"})]
        (should= :error (:status result))
        (should-contain "key" (:message result))))

    (it "requires value parameter"
      (let [result ((:execute sut/set-config-tool) {:key :some-key})]
        (should= :error (:status result))
        (should-contain "value" (:message result)))))

  (context "tool definitions"

    (it "get-config-tool has correct structure"
      (should= :get-config (:name sut/get-config-tool))
      (should-contain "config" (:description sut/get-config-tool))
      (should-not-be-nil (:execute sut/get-config-tool)))

    (it "set-config-tool has correct structure"
      (should= :set-config (:name sut/set-config-tool))
      (should-contain "config" (:description sut/set-config-tool))
      (should-not-be-nil (:execute sut/set-config-tool))))

  (context "registration"

    (it "registers all config tools"
      (sut/register-all!)
      (should-not-be-nil (mdm.isaac.tool.core/get-tool :get-config))
      (should-not-be-nil (mdm.isaac.tool.core/get-tool :set-config)))))
