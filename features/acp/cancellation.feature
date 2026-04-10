Feature: ACP Turn Cancellation
  Clients can interrupt an in-flight prompt turn via session/cancel.

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

  Scenario: session/cancel during a turn stops processing
    When the ACP client sends request 30:
      | key                   | value                       |
      | method                | session/prompt              |
      | params.sessionId      | agent:main:acp:direct:user1 |
      | params.prompt[0].type | text                        |
      | params.prompt[0].text | Long task                   |
    And the ACP client sends notification:
      | key              | value                       |
      | method           | session/cancel              |
      | params.sessionId | agent:main:acp:direct:user1 |
    Then the ACP agent sends response 30:
      | key               | value     |
      | result.stopReason | cancelled |
