Feature: Chat Slash Commands
  Slash commands are intercepted by the session bridge before LLM dispatch.
  The /status command returns session, model, and tool info without calling the LLM.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And the isaac EDN file "config/models/grover.edn" exists with:
      | path | value |
      | model | echo |
      | provider | grover |
      | context-window | 32768 |
    And the isaac EDN file "config/crew/main.edn" exists with:
      | path | value |
      | model | grover |
      | soul | You are Isaac. |

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
