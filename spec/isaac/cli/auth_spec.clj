(ns isaac.cli.auth-spec
  (:require
    [isaac.auth.device-code :as device-code]
    [isaac.auth.store :as auth-store]
    [isaac.cli.auth :as sut]
    [isaac.cli.registry :as registry]
    [isaac.config.loader :as config]
    [speclj.core :refer :all]))

(describe "CLI Auth"

  (around [it]
    (binding [*out* (java.io.StringWriter.)]
      (it)))

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

      (it "returns 1 when --api-key not specified"
        (should= 1 (sut/run ["login" "--provider" "anthropic"])))

      (describe "openai-codex device code flow"

        (it "accepts openai-codex as a known provider"
          (let [output (atom nil)]
            (with-redefs [device-code/request-user-code! (fn [] {:error :api-error :status 404})]
              (binding [*out* (java.io.StringWriter.)]
                (sut/run ["login" "--provider" "openai-codex"])
                (reset! output (str *out*))))
            (should-not-contain "Unknown provider" @output)))

        (it "initiates device code flow without --api-key"
          (let [steps (atom [])]
            (with-redefs [device-code/request-user-code! (fn []
                                                           (swap! steps conj :request-code)
                                                           {:device_auth_id "dauth-1"
                                                            :user_code      "TEST-CODE"
                                                            :interval       5})
                          device-code/poll-for-auth!      (fn [_ _ _]
                                                           (swap! steps conj :poll)
                                                           {:authorization_code "auth-xyz"
                                                            :code_verifier     "verify-abc"})
                          device-code/exchange-tokens!    (fn [_ _]
                                                            (swap! steps conj :exchange)
                                                            {:access_token  "at-ok"
                                                             :refresh_token "rt-ok"
                                                             :id_token      "id-ok"
                                                             :expires_in    3600})
                          auth-store/save-tokens!         (fn [_ _ _]
                                                            (swap! steps conj :save))]
              (should= 0 (sut/run ["login" "--provider" "openai-codex"]))
              (should= [:request-code :poll :exchange :save] @steps))))

        (it "handles string interval from API response"
          (let [poll-interval (atom nil)]
            (with-redefs [device-code/request-user-code! (fn []
                                                           {:device_auth_id "dauth-1"
                                                            :user_code      "TEST-CODE"
                                                            :interval       "5"})
                          device-code/poll-for-auth!      (fn [_ _ interval-ms]
                                                           (reset! poll-interval interval-ms)
                                                           {:authorization_code "ac"
                                                            :code_verifier     "cv"})
                          device-code/exchange-tokens!    (fn [_ _]
                                                            {:access_token  "at"
                                                             :refresh_token "rt"
                                                             :id_token      "id"
                                                             :expires_in    3600})
                          auth-store/save-tokens!         (fn [_ _ _] nil)]
              (should= 0 (sut/run ["login" "--provider" "openai-codex"]))
              (should= 5000 @poll-interval))))

        (it "returns 1 when request-user-code fails"
          (with-redefs [device-code/request-user-code! (fn [] {:error :api-error :status 404})]
            (should= 1 (sut/run ["login" "--provider" "openai-codex"]))))

        (it "returns 1 when poll-for-auth fails"
          (with-redefs [device-code/request-user-code! (fn []
                                                         {:device_auth_id "d"
                                                          :user_code      "C"
                                                          :interval       5})
                        device-code/poll-for-auth!      (fn [_ _ _]
                                                         {:error :timeout})]
            (should= 1 (sut/run ["login" "--provider" "openai-codex"]))))

        (it "returns 1 when token exchange fails"
          (with-redefs [device-code/request-user-code! (fn []
                                                         {:device_auth_id "d"
                                                          :user_code      "C"
                                                          :interval       5})
                        device-code/poll-for-auth!      (fn [_ _ _]
                                                         {:authorization_code "ac"
                                                          :code_verifier     "cv"})
                        device-code/exchange-tokens!    (fn [_ _]
                                                          {:error :api-error})]
            (should= 1 (sut/run ["login" "--provider" "openai-codex"]))))

        ))

    (describe "status"

      (it "returns 0"
        (with-redefs [config/load-config (fn [] {:providers {"ollama" {}}})]
          (should= 0 (sut/run ["status"]))))

      (it "reports anthropic not authenticated"
        (with-redefs [config/load-config (fn [] {:providers {"anthropic" {}}})]
          (should= 0 (sut/run ["status"]))))

      (it "reports anthropic api-key auth"
        (with-redefs [config/load-config (fn [] {:providers {"anthropic" {:api-key "sk-123"}}})]
          (should= 0 (sut/run ["status"])))))

    (describe "logout"

      (it "returns 1 when --provider is missing"
        (should= 1 (sut/run ["logout"])))

      (it "returns 0 for valid provider"
        (should= 0 (sut/run ["logout" "--provider" "anthropic"])))))

  (describe "option parsing"

    (it "dispatches login with parsed --provider requiring --api-key"
      (should= 1 (sut/run ["login" "--provider" "anthropic"])))

    (it "dispatches login with --api-key flag"
      ;; api-key login reads from stdin; ensure credential is stored
      (let [saved (atom nil)]
        (with-redefs [read-line                (fn [] "sk-test-key-123")
                      config/load-config        (fn [] {:stateDir "target/test-auth"})
                      auth-store/save-api-key! (fn [dir provider key]
                                                 (reset! saved [dir provider key]))]
          (should= 0 (sut/run ["login" "--provider" "anthropic" "--api-key"]))
          (should= ["target/test-auth" "anthropic" "sk-test-key-123"] @saved)))))

  (describe "registry integration"

    (it "registers 'auth' command"
      ;; The auth ns registers on load, which has already happened
      (should-not-be-nil (registry/get-command "auth")))

    (it "registered run-fn delegates to auth/run"
      (let [cmd (registry/get-command "auth")]
        ;; Calling with help should return 0
        (should= 0 ((:run-fn cmd) {:_raw-args ["--help"]}))))))
