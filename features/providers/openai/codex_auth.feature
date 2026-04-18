Feature: OpenAI Codex Authentication
  Isaac authenticates with ChatGPT-backed Codex models using
  device-code login state.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And the following models exist:
      | alias | model   | provider      | contextWindow |
      | codex | gpt-5.4 | openai-codex  | 128000        |
    And the following crew exist:
      | name | soul           | model |
      | main | You are Isaac. | codex |

  @slow
  Scenario: Live OpenAI Codex API call
    Given the provider "openai-codex" is configured with:
      | key     | value                     |
      | auth    | oauth-device              |
      | baseUrl | https://api.openai.com/v1 |
      | api     | openai-compatible         |
    And the following sessions exist:
      | name       |
      | codex-live |
    And session "codex-live" has transcript:
      | type    | message.role | message.content |
      | message | user         | Say "hello"     |
    When the user sends "Say \"hello\"" on session "codex-live"
    Then the live "openai-codex" call succeeds or reports missing auth clearly
