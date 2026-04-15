@wip
Feature: ACP Error Response Format
  Provider errors must be sent as agent_message_chunk notifications
  with stopReason: end_turn. The ACP spec does not define an "error"
  stopReason — using one causes clients to show "Internal error."

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following crew exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |
    And the ACP client has initialized

  Scenario: provider error is sent as agent_message_chunk with end_turn
    Given the following sessions exist:
      | name       |
      | error-test |
    And the following model responses are queued:
      | model | type  | content                         |
      | echo  | error | You exceeded your current quota |
    When the ACP client sends request 2:
      | key                   | value          |
      | method                | session/prompt |
      | params.sessionId      | error-test     |
      | params.prompt[0].type | text           |
      | params.prompt[0].text | hello          |
    Then the ACP agent sends notifications:
      | method         | params.update.sessionUpdate |
      | session/update | agent_message_chunk         |
    And the ACP agent sends response 2:
      | key               | value    |
      | result.stopReason | end_turn |

  Scenario: connection refused error is sent as agent_message_chunk with end_turn
    Given the following models exist:
      | alias | model           | provider | contextWindow |
      | local | llama3.2:latest | ollama   | 32000         |
    And the following crew exist:
      | name | soul           | model |
      | main | You are Isaac. | local |
    And the provider "ollama" is configured with:
      | key     | value                  |
      | baseUrl | http://localhost:99999 |
    And the following sessions exist:
      | name        |
      | connect-err |
    When the ACP client sends request 2:
      | key                   | value          |
      | method                | session/prompt |
      | params.sessionId      | connect-err    |
      | params.prompt[0].type | text           |
      | params.prompt[0].text | hello          |
    Then the ACP agent sends notifications:
      | method         | params.update.sessionUpdate |
      | session/update | agent_message_chunk         |
    And the ACP agent sends response 2:
      | key               | value    |
      | result.stopReason | end_turn |
