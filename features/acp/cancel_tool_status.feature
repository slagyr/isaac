Feature: Cancelled Tool Call Status
  When a turn is cancelled while a tool call is in flight, the
  agent must send a tool_call_update with status:cancelled so
  the client can clear the pending indicator.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | context-window |
      | grover | echo  | grover   | 32768          |
    And the following crew exist:
      | name | soul           | model  | tools.allow |
      | main | You are Isaac. | grover | exec        |
    And the built-in tools are registered
    And the ACP client has initialized

  Scenario: cancelled tool call sends a cancelled status update
    Given the following sessions exist:
      | name        |
      | cancel-tool |
    And the following model responses are queued:
      | type      | tool_call | arguments               |
      | tool_call | exec      | {"command": "sleep 30"} |
    When the ACP client sends request 2:
      | key                   | value          |
      | method                | session/prompt |
      | params.sessionId      | cancel-tool    |
      | params.prompt[0].type | text           |
      | params.prompt[0].text | run it         |
    Then the ACP agent sends notifications:
      | method         | params.update.sessionUpdate | params.update.status |
      | session/update | tool_call                   | pending              |
    When the ACP client sends notification:
      | key              | value          |
      | method           | session/cancel |
      | params.sessionId | cancel-tool    |
    Then the ACP agent sends notifications:
      | method         | params.update.sessionUpdate | params.update.status |
      | session/update | tool_call_update            | cancelled            |
