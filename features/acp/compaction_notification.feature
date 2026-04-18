Feature: ACP Compaction Notification
  When a session compacts during a prompt, the ACP client should
  receive an agent_message_chunk notification so the user knows
  why there is a delay before the response.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And the following models exist:
      | alias | model      | provider | contextWindow |
      | local | test-model | grover   | 100           |
    And the following crew exist:
      | name | soul           | model |
      | main | You are Isaac. | local |
    And the ACP client has initialized

  Scenario: compaction sends a status message to the ACP client
    Given the following sessions exist:
      | name         | totalTokens |
      | compact-test | 95          |
    And session "compact-test" has transcript:
      | type    | message.role | message.content            |
      | message | user         | Tell me about compaction   |
      | message | assistant    | It summarizes old messages |
    And the following model responses are queued:
      | type | content               | model      |
      | text | Summary of prior chat | test-model |
      | text | Here is my response   | test-model |
    When the ACP client sends request 2:
      | key                   | value          |
      | method                | session/prompt |
      | params.sessionId      | compact-test   |
      | params.prompt[0].type | text           |
      | params.prompt[0].text | hello          |
    Then the ACP agent sends notifications:
      | method         | params.update.sessionUpdate | params.update.content.text |
      | session/update | agent_message_chunk         | compacting...              |
    And the ACP agent sends response 2:
      | key               | value    |
      | result.stopReason | end_turn |
