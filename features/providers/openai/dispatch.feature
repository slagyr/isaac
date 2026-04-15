Feature: OpenAI Provider Dispatch
  Isaac dispatches to the correct OpenAI API based on provider
  configuration. OAuth Codex providers use chatgpt.com backend
  with streaming. API key providers use chat completions.

  Background:
    Given an empty Isaac state directory "target/test-state"

  Scenario: OAuth Codex provider sends to chatgpt.com backend API
    Given the following models exist:
      | alias  | model        | provider             | contextWindow |
      | snuffy | snuffy-codex | grover:openai-codex  | 128000        |
    And the following crew exist:
      | name  | soul                  | model  |
      | oscar | Lives in a trash can. | snuffy |
    And the following sessions exist:
      | name      | crew  |
      | trash-can | oscar |
    And the following model responses are queued:
      | model        | type | content |
      | snuffy-codex | text | Scram!  |
    When the user sends "knock knock" on session "trash-can"
    Then the last provider request matches:
      | key                        | value                                          |
      | url                        | https://chatgpt.com/backend-api/codex/responses |
      | headers.ChatGPT-Account-Id | #*                                               |
      | headers.originator         | isaac                                            |
      | body.model                 | snuffy-codex                                     |
      | body.instructions          | Lives in a trash can.                            |
      | body.stream                | true                                             |
    And session "trash-can" has transcript matching:
      | type    | message.role | message.content |
      | message | assistant    | Scram!          |

  Scenario: OAuth Codex provider includes conversation history as input
    Given the following models exist:
      | alias  | model        | provider             | contextWindow |
      | snuffy | snuffy-codex | grover:openai-codex  | 128000        |
    And the following crew exist:
      | name  | soul                  | model  |
      | oscar | Lives in a trash can. | snuffy |
    And the following sessions exist:
      | name      | crew  |
      | trash-can | oscar |
    And session "trash-can" has transcript:
      | type    | message.role | message.content |
      | message | user         | knock knock     |
      | message | assistant    | Go away!        |
    And the following model responses are queued:
      | model        | type | content       |
      | snuffy-codex | text | I said SCRAM! |
    When the user sends "knock knock again" on session "trash-can"
    Then the last provider request matches:
      | key                | value     |
      | body.input[0].role | user      |
      | body.input[1].role | assistant |
      | body.input[2].role | user      |

  @wip
  Scenario: OAuth Codex provider formats tools for responses API
    Given the following models exist:
      | alias  | model        | provider             | contextWindow |
      | snuffy | snuffy-codex | grover:openai-codex  | 128000        |
    And the following crew exist:
      | name  | soul                  | model  |
      | oscar | Lives in a trash can. | snuffy |
    And the following sessions exist:
      | name      | crew  |
      | trash-can | oscar |
    And the built-in tools are registered
    And the following model responses are queued:
      | model        | type | content              |
      | snuffy-codex | text | Found a banana peel. |
    When the user sends "what's in the trash?" on session "trash-can"
    Then the last provider request matches:
      | key                           | value    |
      | body.tools[0].type            | function |
      | body.tools[0].name            | read     |
      | body.tools[0].parameters.type | object   |
    And the last provider request does not contain path "body.tools[0].function"

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
