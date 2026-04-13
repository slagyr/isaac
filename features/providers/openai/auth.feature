@wip
Feature: OpenAI Authentication
  Isaac authenticates with the OpenAI API using an API key.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias | model | provider | contextWindow |
      | gpt   | gpt-5 | openai   | 128000        |
    And the following agents exist:
      | name | soul           | model |
      | main | You are Isaac. | gpt   |

  @slow
  Scenario: Live OpenAI API call
    Given the provider "openai" is configured with:
      | key     | value                     |
      | apiKey  | ${OPENAI_API_KEY}         |
      | baseUrl | https://api.openai.com/v1 |
      | api     | openai-compatible         |
    And the following sessions exist:
      | name        |
      | openai-live |
    And session "openai-live" has transcript:
      | type    | message.role | message.content |
      | message | user         | Say "hello"     |
    When the user sends "Say \"hello\"" on session "openai-live"
    Then the live "openai" call succeeds or reports missing auth clearly
