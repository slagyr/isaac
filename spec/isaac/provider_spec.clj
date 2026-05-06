(ns isaac.provider-spec
  (:require
    [c3kit.apron.schema :as schema]
    ;; Loading these triggers their defonce _registration calls — needed by
    ;; the registry tests to see the built-in apis when this spec runs alone.
    [isaac.llm.anthropic]
    [isaac.llm.claude-sdk]
    [isaac.llm.grover]
    [isaac.llm.ollama]
    [isaac.llm.openai-compat]
    [isaac.provider :as sut]
    [speclj.core :refer :all]))

(describe "isaac.provider"

  (describe "error?"

    (it "returns true when :error is present"
      (should (sut/error? {:error :auth-missing :message "no key"})))

    (it "returns false on a normal Response"
      (should-not (sut/error? {:message {:role "assistant" :content "hi"}
                               :model   "x"})))

    (it "returns false on nil"
      (should-not (sut/error? nil))))

  (describe "validate-response"

    (it "accepts a complete Response"
      (let [resp {:message    {:role "assistant" :content "hi"}
                  :model      "claude-sonnet-4-6"
                  :tool-calls []
                  :usage      {:input-tokens 10 :output-tokens 5 :cache-read 0 :cache-write 0}}]
        (should= resp (sut/validate-response resp))))

    (it "accepts a Response with only :message and :model"
      ;; Optional fields can be absent; schema validates types when present.
      (let [resp {:message {:role "assistant" :content "hi"}
                  :model   "x"}]
        (should= resp (sut/validate-response resp))))

    (it "accepts a Response carrying tool-calls"
      (let [resp {:message    {:role "assistant" :content ""
                               :tool_calls [{:function {:name "read" :arguments "{}"}}]}
                  :model      "gpt-5.4"
                  :tool-calls [{:id "tc1" :name "read" :arguments {}
                                :raw {:function {:name "read" :arguments "{}"}}}]
                  :usage      {:input-tokens 11 :output-tokens 4}}]
        (should= resp (sut/validate-response resp))))

    (it "rejects a non-int :input-tokens"
      (should-throw
        (sut/validate-response {:message {:role "assistant" :content "hi"}
                                :usage   {:input-tokens "ten"}}))))

  (describe "tool-call schema"

    (it "accepts a normalized tool-call"
      (let [tc {:id "tc1" :name "read" :arguments {:path "x"}}]
        (should= tc (schema/conform! sut/tool-call tc))))

    (it "accepts a tool-call carrying its raw provider payload"
      (let [tc {:id "tc1" :name "read" :arguments {:path "x"}
                :raw {:function {:name "read" :arguments "{\"path\":\"x\"}"}}}]
        (should= tc (schema/conform! sut/tool-call tc))))

    (it "coerces a numeric :name to a string"
      ;; c3kit schema is coercive — :string accepts coercible values.
      (let [coerced (schema/conform! sut/tool-call {:id "tc1" :name 42 :arguments {}})]
        (should= "42" (:name coerced)))))

  (describe "usage schema"

    (it "accepts the four token fields"
      (let [u {:input-tokens 10 :output-tokens 5 :cache-read 2 :cache-write 1}]
        (should= u (schema/conform! sut/usage u))))

    (it "accepts a partial usage map"
      (let [u {:input-tokens 10 :output-tokens 5}]
        (should= u (schema/conform! sut/usage u))))

    (it "rejects a non-int :output-tokens"
      (should-throw (schema/conform! sut/usage {:input-tokens 10 :output-tokens "five"}))))

  (describe "assistant-message schema"

    (it "accepts a plain text reply"
      (let [m {:role "assistant" :content "hi"}]
        (should= m (schema/conform! sut/assistant-message m))))

    (it "accepts a tool-using assistant message"
      ;; :content can be empty when the turn is purely tool-using; :tool_calls
      ;; carries the provider-native payload.
      (let [m {:role       "assistant"
               :content    ""
               :tool_calls [{:id "tc1" :type "function"
                             :function {:name "read" :arguments "{\"path\":\"x\"}"}}]}]
        (should= m (schema/conform! sut/assistant-message m))))

    )

  (describe "error-response schema"

    (it "accepts a connection-refused error"
      (let [e {:error :connection-refused :message "fail"}]
        (should= e (schema/conform! sut/error-response e))))

    (it "accepts an HTTP error with status and body"
      (let [e {:error :auth-failed :message "bad key" :status 401
               :body  {:error {:type "authentication_error" :message "Invalid API key"}}}]
        (should= e (schema/conform! sut/error-response e))))

    (it "coerces a string :error to a keyword"
      (let [coerced (schema/conform! sut/error-response {:error "auth-failed"})]
        (should= :auth-failed (:error coerced)))))

  (describe "registry"

    (after (sut/unregister! "spec-test"))

    (it "register! adds a factory; factory-for retrieves it"
      (let [calls (atom [])
            f     (fn [name cfg] (swap! calls conj [name cfg]) ::a-provider)]
        (sut/register! "spec-test" f)
        (let [retrieved (sut/factory-for "spec-test")]
          (should= ::a-provider (retrieved "x" {:foo 1}))
          (should= [["x" {:foo 1}]] @calls))))

    (it "registered-apis includes the built-in apis"
      (let [apis (sut/registered-apis)]
        (should-contain "anthropic-messages" apis)
        (should-contain "openai-compatible" apis)
        (should-contain "ollama" apis)
        (should-contain "grover" apis)
        (should-contain "claude-sdk" apis)))

    (it "unregister! removes the factory"
      (sut/register! "spec-test" (fn [_ _] ::p))
      (should (sut/factory-for "spec-test"))
      (sut/unregister! "spec-test")
      (should-be-nil (sut/factory-for "spec-test")))

    (it "register! overwrites a prior factory for the same api"
      (sut/register! "spec-test" (fn [_ _] ::v1))
      (sut/register! "spec-test" (fn [_ _] ::v2))
      (should= ::v2 ((sut/factory-for "spec-test") "x" {}))))

  (describe "simulated-provider-config"

    (it "maps openai-style simulated providers to openai-compatible config"
      (should= {:api "openai-compatible"
                :api-key "grover"
                :base-url "https://api.openai.com/v1"
                :name "openai"
                :simulate-provider "openai"}
               (#'sut/simulated-provider-config "openai"))
      (should= {:api "openai-compatible"
                :api-key "grover"
                :base-url "https://api.openai.com/v1"
                :name "openai-api"
                :simulate-provider "openai-api"}
               (#'sut/simulated-provider-config "openai-api")))

    (it "maps oauth-backed OpenAI variants to chatgpt-compatible config"
      (should= {:api "openai-compatible"
                :auth "oauth-device"
                :base-url "https://api.openai.com/v1"
                :name "openai-chatgpt"
                :simulate-provider "openai-chatgpt"}
               (#'sut/simulated-provider-config "openai-codex"))
      (should= {:api "openai-compatible"
                :auth "oauth-device"
                :base-url "https://api.openai.com/v1"
                :name "openai-chatgpt"
                :simulate-provider "openai-chatgpt"}
               (#'sut/simulated-provider-config "openai-chatgpt")))

    (it "maps grok and returns nil for unknown providers"
      (should= {:api "openai-compatible"
                :api-key "grover"
                :base-url "https://api.x.ai/v1"
                :name "grok"
                :simulate-provider "grok"}
               (#'sut/simulated-provider-config "grok"))
      (should-be-nil (#'sut/simulated-provider-config "mystery")))))
