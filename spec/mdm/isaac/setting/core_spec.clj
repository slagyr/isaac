(ns mdm.isaac.setting.core-spec
  (:require [c3kit.bucket.api :as db]
            [c3kit.bucket.spec-helperc :as helper]
            [mdm.isaac.setting.schema :as schema]
            [mdm.isaac.setting.core :as sut]
            [speclj.core :refer :all]))

(describe "Setting"

  (helper/with-schemas [schema/config])

  (context "get"

    (it "returns nil for non-existent key"
      (should-be-nil (sut/get :non-existent-key)))

    (it "returns value for existing key"
      (db/tx {:kind :config :key :test-key :value "test-value"})
      (should= "test-value" (sut/get :test-key)))

    (it "returns default when key doesn't exist"
      (should= "default" (sut/get :missing-key "default")))

    (it "returns stored value over default when key exists"
      (db/tx {:kind :config :key :existing :value "stored"})
      (should= "stored" (sut/get :existing "default")))

    )

  (context "set!"

    (it "creates new config entry"
      (sut/set! :new-key "new-value")
      (should= "new-value" (sut/get :new-key)))

    (it "updates existing config entry"
      (db/tx {:kind :config :key :update-key :value "old"})
      (sut/set! :update-key "new")
      (should= "new" (sut/get :update-key)))

    (it "returns the saved config entity"
      (let [result (sut/set! :return-key "return-value")]
        (should= :config (:kind result))
        (should= :return-key (:key result))
        (should= "return-value" (:value result))))

    )

  )
