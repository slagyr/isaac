(ns mdm.isaac.secret.core-spec
  (:require [c3kit.apron.env :as env]
            [mdm.isaac.secret.core :as sut]
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

    (it "reads from c3kit.apron.env (supports .env files)"
      (should= (env/env "HOME") (sut/get-secret "HOME")))

    (it "returns nil for non-existent var"
      (should-be-nil (sut/get-secret "NONEXISTENT_VAR_12345")))

    (it "delegates to env/env not System/getenv"
      (with-redefs [env/env (constantly "from-env-fn")]
        (should= "from-env-fn" (sut/get-secret "ANY_KEY")))))

  (context "get-secret :default"
    (with-config {:secret-source {:impl :unknown}})

    (it "throws for unknown implementation"
      (should-throw clojure.lang.ExceptionInfo
                    "Unknown secret source"
                    (sut/get-secret "ANY_KEY")))))
