Feature: ACP Prompt Turn
  session/prompt drives a full chat turn through Isaac's existing
  chat flow, storing messages in the session transcript.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following agents exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |
    And agent "main" has sessions:
      | key                         |
      | agent:main:acp:direct:user1 |
    And the ACP client has initialized

  @wip
  Scenario: A prompt turn stores user and assistant messages
    Given the following model responses are queued:
      | type | content       | model |
      | text | Four, I think | echo  |
    When the ACP client sends request 10:
      | key                   | value                       |
      | method                | session/prompt              |
      | params.sessionId      | agent:main:acp:direct:user1 |
      | params.prompt[0].type | text                        |
      | params.prompt[0].text | What is 2+2?                |
    Then the ACP agent sends response 10:
      | key               | value    |
      | result.stopReason | end_turn |
    And session "agent:main:acp:direct:user1" has transcript matching:
      | type    | message.role | message.content |
      | message | user         | What is 2+2?    |
      | message | assistant    | Four, I think   |

  @wip
  Scenario: Prompt uses the session's configured model and provider
    Given the following model responses are queued:
      | type | content | model |
      | text | Hello   | echo  |
    When the ACP client sends request 11:
      | key                   | value                       |
      | method                | session/prompt              |
      | params.sessionId      | agent:main:acp:direct:user1 |
      | params.prompt[0].type | text                        |
      | params.prompt[0].text | Hi                          |
    Then the ACP agent sends response 11:
      | key               | value    |
      | result.stopReason | end_turn |
    And session "agent:main:acp:direct:user1" has transcript matching:
      | type    | message.role | message.model | message.provider |
      | message | assistant    | echo          | grover           |
