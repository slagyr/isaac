Feature: OpenAI Codex Authentication
  Isaac authenticates with ChatGPT-backed Codex models using
  device-code login state.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias | model   | provider      | contextWindow |
      | codex | gpt-5.4 | openai-codex  | 128000        |
    And the following agents exist:
      | name | soul           | model |
      | main | You are Isaac. | codex |

  @slow
  Scenario: Live OpenAI Codex API call
    Given the provider "openai-codex" is configured with:
      | key     | value                     |
      | auth    | oauth-device              |
      | baseUrl | https://api.openai.com/v1 |
      | api     | openai-compatible         |
    And agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Say "hello"     |
    When the user sends "Say \"hello\"" on session "agent:main:cli:direct:user1"
    Then the live "openai-codex" call succeeds or reports missing auth clearly
