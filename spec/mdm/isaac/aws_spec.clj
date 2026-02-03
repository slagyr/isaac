(ns mdm.isaac.aws-spec
  (:require [mdm.isaac.aws :as sut]
            [mdm.isaac.config :as config]
            [speclj.core :refer :all])
  (:import (software.amazon.awssdk.auth.credentials InstanceProfileCredentialsProvider StaticCredentialsProvider)))

(describe "AWS"

  (context "make-credentials-provider"

    (it "returns StaticCredentialsProvider in dev"
      (with-redefs [config/development? true
                    sut/access-key      "test-access"
                    sut/secret-key      "test-secret"]
        (should (instance? StaticCredentialsProvider (sut/make-credentials-provider)))))

    (it "returns InstanceProfileCredentialsProvider in prod"
      (with-redefs [config/development? false]
        (should (instance? InstanceProfileCredentialsProvider (sut/make-credentials-provider))))))

)
