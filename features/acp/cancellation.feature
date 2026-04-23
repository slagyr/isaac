Feature: ACP Turn Cancellation
  Clients can interrupt an in-flight prompt turn via session/cancel.

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
    And the following sessions exist:
      | name        |
      | cancel-test |
    And the ACP client has initialized

  Scenario: session/cancel during a turn stops processing
    When the ACP client sends request 30:
      | key                   | value          |
      | method                | session/prompt |
      | params.sessionId      | cancel-test    |
      | params.prompt[0].type | text           |
      | params.prompt[0].text | Long task      |
    And the ACP client sends notification:
      | key              | value          |
      | method           | session/cancel |
      | params.sessionId | cancel-test    |
    Then the ACP agent sends response 30:
      | key               | value     |
      | result.stopReason | cancelled |
