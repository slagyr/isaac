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
  Scenario: API key sent in Authorization header
    Given the provider "openai" is configured with:
      | key     | value                     |
      | apiKey  | ${OPENAI_API_KEY}         |
      | baseUrl | https://api.openai.com/v1 |
      | api     | openai-compatible         |
    And agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Hello           |
    When the user sends "Hello" on session "agent:main:cli:direct:user1"
    Then the request header "Authorization" matches #"Bearer .+"

  @slow
  Scenario: Invalid API key returns auth error
    Given the provider "openai" is configured with:
      | key     | value                     |
      | apiKey  | invalid-key               |
      | baseUrl | https://api.openai.com/v1 |
      | api     | openai-compatible         |
    And agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Hello           |
    When the user sends "Hello" on session "agent:main:cli:direct:user1"
    Then an error is reported indicating authentication failed

  @slow
  Scenario: Live OpenAI API call
    Given the provider "openai" is configured with:
      | key     | value                     |
      | apiKey  | ${OPENAI_API_KEY}         |
      | baseUrl | https://api.openai.com/v1 |
      | api     | openai-compatible         |
    And agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Say "hello"     |
    When the user sends "Say \"hello\"" on session "agent:main:cli:direct:user1"
    Then the live "openai" call succeeds or reports missing auth clearly
