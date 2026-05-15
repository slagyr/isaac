(ns isaac.llm.providers-spec
  (:require
    [isaac.config.loader :as config]
    [isaac.llm.providers :as sut]
    [speclj.core :refer :all]))

(describe "isaac.llm.providers"

  (after (sut/unregister! "test-provider"))

  (describe "defaults"

    (it "returns nil for unknown providers"
      (should-be-nil (sut/defaults "mystery"))
      (should-be-nil (sut/defaults "")))

    (it "returns anthropic-messages config for anthropic"
      (let [d (sut/defaults "anthropic")]
        (should= "anthropic-messages" (:api d))
        (should= "https://api.anthropic.com" (:base-url d))
        (should= "api-key" (:auth d))))

    (it "returns ollama config with default base-url and no auth"
      (let [d (sut/defaults "ollama")]
        (should= "ollama" (:api d))
        (should= "http://localhost:11434" (:base-url d))
        (should= "none" (:auth d))
        (should= nil (:models d))))

    (it "returns openai-completions config for openai with api-key auth"
      (let [d (sut/defaults "openai")]
        (should= "openai-completions" (:api d))
        (should= "https://api.openai.com/v1" (:base-url d))
        (should= "api-key" (:auth d))
        (should-be-nil (:models d))))

    (it "returns openai-completions config for grok with api-key auth"
      (let [d (sut/defaults "xai")]
        (should= "openai-completions" (:api d))
        (should= "https://api.x.ai/v1" (:base-url d))
        (should= "api-key" (:auth d))))

    (it "returns openai-responses config for openai-chatgpt with oauth-device"
      (let [d (sut/defaults "openai-chatgpt")]
        (should= "openai-responses" (:api d))
        (should= "oauth-device" (:auth d))
        (should= "https://chatgpt.com/backend-api/codex" (:base-url d))
        (should-be-nil (:models d))))

    (it "returns claude-sdk config with none auth"
      (let [d (sut/defaults "claude-sdk")]
        (should= "claude-sdk" (:api d))
        (should= "none" (:auth d))
        (should-be-nil (:models d))))

    (it "returns grover config with none auth and empty models"
      (let [d (sut/defaults "grover")]
        (should= "grover" (:api d))
        (should= "none" (:auth d))
        (should= nil (:models d)))))

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
      (let [d (sut/grover-defaults "xai")]
        (should= "xai" (:simulate-provider d))
        (should= "grover" (:api-key d))
        (should= "openai-completions" (:api d))))

    (it "adds :simulate-provider but no :api-key for oauth-device providers"
      (let [d (sut/grover-defaults "openai-chatgpt")]
        (should= "openai-chatgpt" (:simulate-provider d))
        (should-be-nil (:api-key d))
        (should= "openai-responses" (:api d))
        (should= "oauth-device" (:auth d))))

    )

  (describe "known-providers"

    (it "includes all built-in provider names"
      (let [known (sut/known-providers)]
        (should-contain "anthropic" known)
        (should-contain "claude-sdk" known)
        (should-contain "grover" known)
        (should-contain "ollama" known)
        (should-contain "openai" known)
        (should-contain "openai-chatgpt" known)
        (should-contain "xai" known))))

  (describe "registry"

    (it "registers and looks up a provider entry"
      (sut/register! "test-provider" {:api "openai-completions" :base-url "https://example.test"})
      (should= {:api "openai-completions" :base-url "https://example.test"}
               (select-keys (sut/defaults "test-provider") [:api :base-url])))

    (it "resolves a user-defined provider override on top of a built-in provider"
      (let [cfg {:providers {:anthropic {:api-key "corp-secret"}}}
            p   (sut/lookup cfg nil "anthropic")]
        (should= "anthropic-messages" (:api p))
        (should= "https://api.anthropic.com" (:base-url p))
        (should= "corp-secret" (:api-key p))))

    (it "resolves :type inheritance from a built-in provider"
      (let [cfg {:providers {:corp-anthropic {:type     :anthropic
                                              :base-url "https://corp.example"
                                              :api-key  "corp-secret"}}}
            p   (sut/lookup cfg nil "corp-anthropic")]
        (should= "anthropic-messages" (:api p))
        (should= "https://corp.example" (:base-url p))
        (should= "corp-secret" (:api-key p))))

    (it "resolves a user provider inheriting from a module-declared provider"
      (let [cfg          {:providers {:fizzy-staging {:type :kombucha :api-key "staging-key"}}}
            module-index {:isaac.providers.kombucha {:manifest {:provider {:kombucha {:template {:api      "openai-completions"
                                                                                                  :base-url "https://api.kombucha.test/v1"
                                                                                                  :auth     "api-key"
                                                                                                  :models   ["kombucha-large" "kombucha-small"]}}}}}}
            p            (sut/lookup cfg module-index "fizzy-staging")]
        (should= "openai-completions" (:api p))
        (should= "https://api.kombucha.test/v1" (:base-url p))
        (should= "staging-key" (:api-key p))))))
