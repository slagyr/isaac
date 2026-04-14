@wip
Feature: Chat Slash Commands
  Slash commands are intercepted by the session bridge before LLM dispatch.
  The /status command returns session, model, and tool info without calling the LLM.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following crew exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |

  Scenario: /status via ACP responds with end_turn without calling the LLM
    Given the following sessions exist:
      | name          |
      | slash-status  |
    And the ACP client has initialized
    When the ACP client sends request 10:
      | key                   | value          |
      | method                | session/prompt |
      | params.sessionId      | slash-status   |
      | params.prompt[0].type | text           |
      | params.prompt[0].text | /status        |
    Then the ACP agent sends response 10:
      | key               | value    |
      | result.stopReason | end_turn |

  Scenario: /status via ACP sends a chat/status notification
    Given the following sessions exist:
      | name          |
      | slash-status  |
    And the ACP client has initialized
    When the ACP client sends request 11:
      | key                   | value          |
      | method                | session/prompt |
      | params.sessionId      | slash-status   |
      | params.prompt[0].type | text           |
      | params.prompt[0].text | /status        |
    Then the ACP agent sends notifications:
      | method      | params.crew |
      | chat/status | main        |
