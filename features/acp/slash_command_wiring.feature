Feature: ACP Slash Command Wiring
  Slash commands must work through the ACP path. The bridge
  needs crew-members and models in its context to resolve
  /crew and /model commands.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model         | provider | contextWindow |
      | grover | echo          | grover   | 32768         |
      | grok   | grok-4-1-fast | grok     | 32768         |
    And the following crew exist:
      | name  | soul              | model  |
      | main  | You are Isaac.    | grover |
      | ketch | You are a pirate. | grover |
    And the ACP client has initialized

  Scenario: /crew switches crew member through ACP
    Given the following sessions exist:
      | name      |
      | crew-test |
    When the ACP client sends request 2:
      | key                   | value          |
      | method                | session/prompt |
      | params.sessionId      | crew-test      |
      | params.prompt[0].type | text           |
      | params.prompt[0].text | /crew ketch    |
    Then the ACP agent sends response 2:
      | key               | value    |
      | result.stopReason | end_turn |
    And the ACP agent sends notifications:
      | method         | params.update.sessionUpdate |
      | session/update | agent_message_chunk         |

  Scenario: /model switches model through ACP
    Given the following sessions exist:
      | name       |
      | model-test |
    When the ACP client sends request 2:
      | key                   | value          |
      | method                | session/prompt |
      | params.sessionId      | model-test     |
      | params.prompt[0].type | text           |
      | params.prompt[0].text | /model grok    |
    Then the ACP agent sends response 2:
      | key               | value    |
      | result.stopReason | end_turn |
    And the ACP agent sends notifications:
      | method         | params.update.sessionUpdate |
      | session/update | agent_message_chunk         |
