Feature: Grok Authentication
  Isaac authenticates with xAI's Grok API using an API key.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And the following models exist:
      | alias | model         | provider | context-window |
      | grok  | grok-4-1-fast | grok     | 131072         |
    And the following crew exist:
      | name | soul           | model |
      | main | You are Isaac. | grok  |

  @slow
  Scenario: Invalid API key returns auth error
    Given the provider "grok" is configured with:
      | key     | value                |
      | api-key | invalid-key          |
      | base-url | https://api.x.ai/v1 |
      | api     | openai-compatible    |
    And the following sessions exist:
      | name         |
      | grok-invalid |
    And session "grok-invalid" has transcript:
      | type    | message.role | message.content |
      | message | user         | Hello           |
    When the user sends "Hello" on session "grok-invalid"
    Then an error is reported indicating authentication failed

  @slow
  Scenario: Live Grok API call
    Given the provider "grok" is configured with:
      | key     | value                |
      | api-key | ${GROK_API_KEY}      |
      | base-url | https://api.x.ai/v1 |
      | api     | openai-compatible    |
    And the following sessions exist:
      | name      |
      | grok-live |
    And session "grok-live" has transcript:
      | type    | message.role | message.content |
      | message | user         | Say "hello"     |
    When the user sends "Say \"hello\"" on session "grok-live"
    Then the live "grok" call succeeds or reports missing auth clearly
