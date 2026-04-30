(ns isaac.provider-spec
  (:require
    [c3kit.apron.schema :as schema]
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
        (should= :auth-failed (:error coerced))))))
