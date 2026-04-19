Feature: OpenAI Authentication
  Isaac authenticates with the OpenAI API using an API key.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And the following models exist:
      | alias | model | provider | context-window |
      | gpt   | gpt-5 | openai   | 128000         |
    And the following crew exist:
      | name | soul           | model |
      | main | You are Isaac. | gpt   |

  @slow
  Scenario: Live OpenAI API call
    Given the provider "openai" is configured with:
      | key     | value                     |
      | api-key | ${OPENAI_API_KEY}         |
      | base-url | https://api.openai.com/v1 |
      | api     | openai-compatible         |
    And the following sessions exist:
      | name        |
      | openai-live |
    And session "openai-live" has transcript:
      | type    | message.role | message.content |
      | message | user         | Say "hello"     |
    When the user sends "Say \"hello\"" on session "openai-live"
    Then the live "openai" call succeeds or reports missing auth clearly
