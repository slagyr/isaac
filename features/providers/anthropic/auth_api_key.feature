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

  @wip
  Scenario: API key sent in request header
    Given the provider "anthropic" is configured with:
      | key     | value                     |
      | auth    | api-key                   |
      | apiKey  | ${ANTHROPIC_API_KEY}      |
      | baseUrl | https://api.anthropic.com |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When the prompt is sent to the LLM
    Then the request header "x-api-key" is present
    And the request header "anthropic-version" is present

  @wip
  Scenario: Invalid API key returns auth error
    Given the provider "anthropic" is configured with:
      | key    | value          |
      | auth   | api-key        |
      | apiKey | sk-ant-invalid |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When the prompt is sent to the LLM
    Then an error is reported indicating authentication failed

  @wip @slow
  Scenario: Live API key authentication
    Given the provider "anthropic" is configured with:
      | key     | value                     |
      | auth    | api-key                   |
      | apiKey  | ${ANTHROPIC_API_KEY}      |
      | baseUrl | https://api.anthropic.com |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content     |
      | user | Say "hello" |
    When the prompt is sent to the LLM
    Then the transcript has entries matching:
      | type    | message.role | message.provider |
      | message | assistant    | anthropic        |

  @wip @slow
  Scenario: Live streaming with API key
    Given the provider "anthropic" is configured with:
      | key     | value                     |
      | auth    | api-key                   |
      | apiKey  | ${ANTHROPIC_API_KEY}      |
      | baseUrl | https://api.anthropic.com |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content         |
      | user | Tell me a story |
    When the prompt is streamed to the LLM
    Then response chunks arrive incrementally
    And the transcript has entries matching:
      | type    | message.role | message.provider |
      | message | assistant    | anthropic        |
