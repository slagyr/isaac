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
    And agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Hello           |
    When the user sends "Hello" on session "agent:main:cli:direct:user1"
    Then the request includes header "x-api-key"
    And the request includes header "anthropic-version"

  @wip
  Scenario: Invalid API key returns auth error
    Given the provider "anthropic" is configured with:
      | key    | value          |
      | auth   | api-key        |
      | apiKey | sk-ant-invalid |
    And agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Hello           |
    When the user sends "Hello" on session "agent:main:cli:direct:user1"
    Then an error is reported indicating authentication failed

  @slow
  Scenario: Live API key authentication
    Given the provider "anthropic" is configured with:
      | key     | value                     |
      | auth    | api-key                   |
      | apiKey  | ${ANTHROPIC_API_KEY}      |
      | baseUrl | https://api.anthropic.com |
    And agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Say "hello"     |
    When the user sends "Say \"hello\"" on session "agent:main:cli:direct:user1"
    Then the live "anthropic" call succeeds or reports missing auth clearly

  @wip @slow
  Scenario: Live streaming with API key
    Given the provider "anthropic" is configured with:
      | key     | value                     |
      | auth    | api-key                   |
      | apiKey  | ${ANTHROPIC_API_KEY}      |
      | baseUrl | https://api.anthropic.com |
    And agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Tell me a story |
    When the user sends "Tell me a story" on session "agent:main:cli:direct:user1"
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | type    | message.role | message.provider |
      | message | assistant    | anthropic        |
