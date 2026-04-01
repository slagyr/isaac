Feature: Grok Authentication
  Isaac authenticates with xAI's Grok API using an API key.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias | model         | provider | contextWindow |
      | grok  | grok-4-1-fast | grok     | 131072        |
    And the following agents exist:
      | name | soul           | model |
      | main | You are Isaac. | grok  |

  @wip
  Scenario: API key sent in Authorization header
    Given the provider "grok" is configured with:
      | key     | value                |
      | apiKey  | ${GROK_API_KEY}      |
      | baseUrl | https://api.x.ai/v1 |
      | api     | openai-compatible    |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When the prompt is sent to the LLM
    Then the request header "Authorization" matches #"Bearer .+"

  @wip
  Scenario: Invalid API key returns auth error
    Given the provider "grok" is configured with:
      | key     | value                |
      | apiKey  | invalid-key          |
      | baseUrl | https://api.x.ai/v1 |
      | api     | openai-compatible    |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When the prompt is sent to the LLM
    Then an error is reported indicating authentication failed

  @wip @slow
  Scenario: Live Grok API call
    Given the provider "grok" is configured with:
      | key     | value                |
      | apiKey  | ${GROK_API_KEY}      |
      | baseUrl | https://api.x.ai/v1 |
      | api     | openai-compatible    |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content     |
      | user | Say "hello" |
    When the prompt is sent to the LLM
    Then the transcript has entries matching:
      | type    | message.role | message.provider |
      | message | assistant    | grok             |
