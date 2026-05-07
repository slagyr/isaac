(ns isaac.llm.providers-spec
  (:require
    [isaac.llm.providers :as sut]
    [speclj.core :refer :all]))

(describe "isaac.llm.providers"

  (describe "defaults"

    (it "returns nil for unknown providers"
      (should-be-nil (sut/defaults "mystery"))
      (should-be-nil (sut/defaults "")))

    (it "returns anthropic-messages config for anthropic"
      (should= "anthropic-messages" (:api (sut/defaults "anthropic"))))

    (it "returns ollama config with default base-url"
      (let [d (sut/defaults "ollama")]
        (should= "ollama" (:api d))
        (should= "http://localhost:11434" (:base-url d))))

    (it "returns openai-completions config for openai"
      (let [d (sut/defaults "openai")]
        (should= "openai-completions" (:api d))
        (should= "https://api.openai.com/v1" (:base-url d))
        (should= "openai" (:name d))
        (should-be-nil (:auth d))))

    (it "returns openai-completions config for openai-api"
      (let [d (sut/defaults "openai-api")]
        (should= "openai-completions" (:api d))
        (should= "openai-api" (:name d))))

    (it "returns openai-completions config for grok"
      (let [d (sut/defaults "grok")]
        (should= "openai-completions" (:api d))
        (should= "https://api.x.ai/v1" (:base-url d))
        (should= "grok" (:name d))))

    (it "returns openai-responses config for openai-chatgpt with oauth-device"
      (let [d (sut/defaults "openai-chatgpt")]
        (should= "openai-responses" (:api d))
        (should= "openai-chatgpt" (:name d))
        (should= "oauth-device" (:auth d))))

    (it "returns openai-responses config for openai-codex (aliased to chatgpt)"
      (let [d (sut/defaults "openai-codex")]
        (should= "openai-responses" (:api d))
        (should= "openai-chatgpt" (:name d))
        (should= "oauth-device" (:auth d))))

    (it "returns claude-sdk config"
      (should= "claude-sdk" (:api (sut/defaults "claude-sdk"))))

    (it "returns grover config"
      (should= "grover" (:api (sut/defaults "grover")))))

  (describe "grover-defaults"

    (it "returns nil for unknown providers"
      (should-be-nil (sut/grover-defaults "mystery")))

    (it "adds :simulate-provider and :api-key grover for api-key providers"
      (let [d (sut/grover-defaults "openai")]
        (should= "openai" (:simulate-provider d))
        (should= "grover" (:api-key d))
        (should= "openai-completions" (:api d))
        (should= "https://api.openai.com/v1" (:base-url d))))

    (it "adds :simulate-provider and :api-key grover for grok"
      (let [d (sut/grover-defaults "grok")]
        (should= "grok" (:simulate-provider d))
        (should= "grover" (:api-key d))
        (should= "openai-completions" (:api d))))

    (it "adds :simulate-provider but no :api-key for oauth-device providers"
      (let [d (sut/grover-defaults "openai-chatgpt")]
        (should= "openai-chatgpt" (:simulate-provider d))
        (should-be-nil (:api-key d))
        (should= "openai-responses" (:api d))
        (should= "oauth-device" (:auth d))))

    (it "adds :simulate-provider but no :api-key for openai-codex"
      (let [d (sut/grover-defaults "openai-codex")]
        (should= "openai-codex" (:simulate-provider d))
        (should-be-nil (:api-key d))
        (should= "openai-responses" (:api d)))))

  (describe "known-providers"

    (it "includes all built-in provider names"
      (let [known (sut/known-providers)]
        (should-contain "anthropic" known)
        (should-contain "claude-sdk" known)
        (should-contain "grover" known)
        (should-contain "ollama" known)
        (should-contain "openai" known)
        (should-contain "openai-api" known)
        (should-contain "openai-codex" known)
        (should-contain "openai-chatgpt" known)
        (should-contain "grok" known)))))
