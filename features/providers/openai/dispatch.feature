Feature: OpenAI Provider Dispatch
  Isaac dispatches to the correct OpenAI API based on provider
  configuration. OAuth providers use the responses API with
  streaming. API key providers use chat completions.

  Background:
    Given an empty Isaac state directory "target/test-state"

  Scenario: OAuth provider sends responses API request
    Given the following models exist:
      | alias    | model    | provider            | contextWindow |
      | big-bird | big-bird | grover:openai-codex | 32768         |
    And the following crew exist:
      | name   | soul           | model    |
      | marvin | You are Marvin | big-bird |
    And the following sessions exist:
      | name            | crew   |
      | sesame-workshop | marvin |
    And the following model responses are queued:
      | model    | type | content                     |
      | big-bird | text | Can you tell me how to get? |
    When the user sends "hi" on session "sesame-workshop"
    Then the last provider request matches:
      | key         | value                               |
      | url         | https://api.openai.com/v1/responses |
      | body.model  | big-bird                            |
      | body.stream | true                                |
    And session "sesame-workshop" has transcript matching:
      | type    | message.role | message.content             |
      | message | assistant    | Can you tell me how to get? |

  Scenario: API key provider sends chat completions request
    Given the following models exist:
      | alias  | model  | provider      | contextWindow |
      | cookie | cookie | grover:openai | 32768         |
    And the following crew exist:
      | name     | soul            | model  |
      | cmonster | Me love cookie! | cookie |
    And the following sessions exist:
      | name       | crew     |
      | cookie-jar | cmonster |
    And the following model responses are queued:
      | model  | type | content          |
      | cookie | text | C is for cookie! |
    When the user sends "hi" on session "cookie-jar"
    Then the last provider request matches:
      | key        | value                                      |
      | url        | https://api.openai.com/v1/chat/completions |
      | body.model | cookie                                     |
    And session "cookie-jar" has transcript matching:
      | type    | message.role | message.content  |
      | message | assistant    | C is for cookie! |
