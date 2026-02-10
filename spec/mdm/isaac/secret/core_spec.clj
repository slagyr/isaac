(ns mdm.isaac.secret.core-spec
  (:require [mdm.isaac.secret.core :as sut]
            [mdm.isaac.spec-helper :refer [with-config]]
            [speclj.core :refer :all]))

(describe "secret.core"

  (context "secret-impl"

    (it "returns configured implementation keyword"
      (should= :env (sut/secret-impl)))

    (context "with custom config"
      (with-config {:secret-source {:impl :aws}})

      (it "returns aws when configured"
        (should= :aws (sut/secret-impl)))))

  (context "get-secret :env"

    (it "returns environment variable value"
      (should= (System/getenv "HOME") (sut/get-secret "HOME")))

    (it "returns nil for non-existent env var"
      (should-be-nil (sut/get-secret "NONEXISTENT_VAR_12345"))))

  (context "get-secret :default"
    (with-config {:secret-source {:impl :unknown}})

    (it "throws for unknown implementation"
      (should-throw clojure.lang.ExceptionInfo
                    "Unknown secret source"
                    (sut/get-secret "ANY_KEY")))))
