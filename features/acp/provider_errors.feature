Feature: ACP Provider Error Surfacing
  When a provider returns an error (quota exceeded, rate limited,
  auth failure, server error), the ACP client must receive a
  readable error message — not a silent failure.

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
    And the ACP client has initialized

  Scenario: quota exceeded error is surfaced to the client
    Given the following sessions exist:
      | name       |
      | quota-test |
    And the following model responses are queued:
      | type  | content                           | model |
      | error | You exceeded your current quota   | echo  |
    When the ACP client sends request 2:
      | key                   | value          |
      | method                | session/prompt |
      | params.sessionId      | quota-test     |
      | params.prompt[0].type | text           |
      | params.prompt[0].text | hello          |
    Then the ACP agent sends notifications:
      | method         | params.update.sessionUpdate | params.update.content.text      |
      | session/update | agent_message_chunk         | You exceeded your current quota |
    And the ACP agent sends response 2:
      | key               | value    |
      | result.stopReason | end_turn |

  Scenario: connection refused error is surfaced to the client
    Given the following sessions exist:
      | name            |
      | connect-refused |
    And the provider "ollama" is configured with:
      | key      | value                  |
      | base-url | http://localhost:99999 |
    And the isaac EDN file "config/crew/main.edn" exists with:
      | path | value |
      | model | local |
      | soul | You are Isaac. |
    And the isaac EDN file "config/models/local.edn" exists with:
      | path | value |
      | model | llama3.2:latest |
      | provider | ollama |
      | context-window | 32000 |
    When the ACP client sends request 2:
      | key                   | value            |
      | method                | session/prompt   |
      | params.sessionId      | connect-refused  |
      | params.prompt[0].type | text             |
      | params.prompt[0].text | hello            |
    Then the ACP agent sends notifications:
      | method         | params.update.sessionUpdate |
      | session/update | agent_message_chunk         |
    And the ACP agent sends response 2:
      | key               | value    |
      | result.stopReason | end_turn |
