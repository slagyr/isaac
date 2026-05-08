Feature: Api extension
  Modules can ship new wire-format Api implementations by declaring
  :extends {:api {<name> {}}} in their manifest, providing an :entry
  namespace, and calling isaac.llm.api/register! from -isaac-init.

  An Api is the protocol-implementing code (chat, chat-stream,
  followup-messages, config, display-name) that adapts an upstream
  service's wire format. The Provider concept (separate, configured via
  :providers) points at one of these Apis with base-url, auth, and model
  list.

  Most third-party additions are Providers (data, no code) — see
  features/modules/provider_extension.feature. This feature covers the
  rarer case: shipping a brand-new wire format that isn't one of Isaac's
  built-in six (anthropic-messages, openai-completions, openai-responses,
  claude-sdk, ollama, grover).

  @wip
  Scenario: A module-shipped Api can serve a provider
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules   {:isaac.api.tin-can {:local/root "modules/isaac.api.tin-can"}}
       :providers {:tin-test {:api    "tin-can"
                              :auth   "none"
                              :models ["echo-1"]}}
       :crew      {:main {:provider :tin-test :model "echo-1"}}}
      """
    When the user sends "what is your purpose" on session "main" via memory comm
    Then the reply contains "tin-can heard: what is your purpose"

  @wip
  Scenario: Module-shipped Api activation is logged
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:log       {:output :memory}
       :modules   {:isaac.api.tin-can {:local/root "modules/isaac.api.tin-can"}}
       :providers {:tin-test {:api    "tin-can"
                              :auth   "none"
                              :models ["echo-1"]}}
       :crew      {:main {:provider :tin-test :model "echo-1"}}}
      """
    When the user sends "ping" on session "main" via memory comm
    Then the log has entries matching:
      | level | event             | module            |
      | :info | :module/activated | isaac.api.tin-can |
    And the log has entries matching:
      | level | event           | api     |
      | :info | :api/registered | tin-can |

  @wip
  Scenario: Provider validation fails when the api's module is not declared
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:providers {:tin-test {:api    "tin-can"
                              :auth   "none"
                              :models ["echo-1"]}}}
      """
    When the config is loaded
    Then the config has validation errors matching:
      | key                    | value       |
      | providers.tin-test.api | unknown api |

  @wip
  Scenario: A turn against an unregistered api fails with a useful reply
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:providers {:bogus {:api    "carrier-pigeon"
                           :auth   "none"
                           :models ["mythical"]}}
       :crew      {:main {:provider :bogus :model "mythical"}}}
      """
    When the user sends "hello" on session "main" via memory comm
    Then the reply contains "unknown api: carrier-pigeon"
