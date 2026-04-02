(ns isaac.cli.auth-spec
  (:require
    [isaac.auth.oauth :as oauth]
    [isaac.cli.auth :as sut]
    [isaac.cli.registry :as registry]
    [isaac.config.resolution :as config]
    [speclj.core :refer :all]))

(describe "CLI Auth"

  (describe "run"

    (it "prints help and returns 0 with no subcommand"
      (should= 0 (sut/run [])))

    (it "prints help and returns 0 with --help"
      (should= 0 (sut/run ["--help"])))

    (it "returns 1 for unknown subcommand"
      (should= 1 (sut/run ["unknown-sub"])))

    (describe "login"

      (it "returns 1 when --provider is missing"
        (should= 1 (sut/run ["login"])))

      (it "returns 1 for unknown provider"
        (should= 1 (sut/run ["login" "--provider" "unknown-provider"])))

      (it "calls OAuth login for known provider"
        (with-redefs [oauth/resolve-token (fn [] {:accessToken "tok"})]
          (should= 0 (sut/run ["login" "--provider" "anthropic"]))))

      (it "returns 1 when OAuth fails"
        (with-redefs [oauth/resolve-token (fn [] {:error :oauth-refresh-failed :message "fail"})]
          (should= 1 (sut/run ["login" "--provider" "anthropic"])))))

    (describe "status"

      (it "returns 0"
        (with-redefs [config/load-config (fn [] {:models {:providers [{:name "ollama"}]}})]
          (should= 0 (sut/run ["status"]))))

      (it "reports anthropic auth status with credentials"
        (with-redefs [config/load-config  (fn [] {:models {:providers [{:name "anthropic"}]}})
                      oauth/read-credentials (fn [& _] {:accessToken "tok"})]
          (should= 0 (sut/run ["status"]))))

      (it "reports anthropic not authenticated"
        (with-redefs [config/load-config  (fn [] {:models {:providers [{:name "anthropic"}]}})
                      oauth/read-credentials (fn [& _] nil)]
          (should= 0 (sut/run ["status"]))))

      (it "reports anthropic api-key auth"
        (with-redefs [config/load-config  (fn [] {:models {:providers [{:name "anthropic" :apiKey "sk-123"}]}})
                      oauth/read-credentials (fn [& _] nil)]
          (should= 0 (sut/run ["status"])))))

    (describe "logout"

      (it "returns 1 when --provider is missing"
        (should= 1 (sut/run ["logout"])))

      (it "returns 0 for valid provider"
        (should= 0 (sut/run ["logout" "--provider" "anthropic"])))))

  (describe "option parsing"

    (it "dispatches login with parsed --provider"
      (with-redefs [oauth/resolve-token (fn [] {:accessToken "tok"})]
        (should= 0 (sut/run ["login" "--provider" "anthropic"]))))

    (it "dispatches login with --api-key flag"
      ;; api-key login reads from stdin; we redef read-line
      (with-redefs [read-line     (fn [] "sk-test-key-123")
                    config/load-config (fn [] {:stateDir "target/test-auth"})]
        (should= 0 (sut/run ["login" "--provider" "anthropic" "--api-key"])))))

  (describe "registry integration"

    (it "registers 'auth' command"
      ;; The auth ns registers on load, which has already happened
      (should-not-be-nil (registry/get-command "auth")))

    (it "registered run-fn delegates to auth/run"
      (let [cmd (registry/get-command "auth")]
        ;; Calling with help should return 0
        (should= 0 ((:run-fn cmd) {:_raw-args ["--help"]}))))))
