Feature: Anthropic API Key Authentication
  Isaac authenticates with the Anthropic Messages API using
  an API key from environment variables.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model             | provider  | contextWindow |
      | claude | claude-sonnet-4-6 | anthropic | 200000        |
    And the following agents exist:
      | name | soul           | model  |
      | main | You are Isaac. | claude |

  @slow
  Scenario: Invalid API key returns auth error
    Given the provider "anthropic" is configured with:
      | key    | value          |
      | auth   | api-key        |
      | apiKey | sk-ant-invalid |
    And the following sessions exist:
      | name              |
      | anthropic-invalid |
    And session "anthropic-invalid" has transcript:
      | type    | message.role | message.content |
      | message | user         | Hello           |
    When the user sends "Hello" on session "anthropic-invalid"
    Then an error is reported indicating authentication failed

  @slow
  Scenario: Live API key authentication
    Given the provider "anthropic" is configured with:
      | key     | value                     |
      | auth    | api-key                   |
      | apiKey  | ${ANTHROPIC_API_KEY}      |
      | baseUrl | https://api.anthropic.com |
    And the following sessions exist:
      | name           |
      | anthropic-live |
    And session "anthropic-live" has transcript:
      | type    | message.role | message.content |
      | message | user         | Say "hello"     |
    When the user sends "Say \"hello\"" on session "anthropic-live"
    Then the live "anthropic" call succeeds or reports missing auth clearly
