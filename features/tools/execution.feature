@wip
Feature: Tool Execution

  When an LLM returns tool calls, Isaac executes the requested tools
  and feeds the results back to the LLM for continued reasoning.

  Background:
    Given an empty Isaac state directory "test-state"
    And the following models exist:
      | name       | provider | context |
      | test-model | grover   | 8000    |
    And the following agents exist:
      | name | model      | soul            |
      | main | test-model | You are helpful |
    And the agent has tools:
      | name  | description              |
      | read  | Read a file's contents   |
      | write | Write content to a file  |
      | edit  | Replace text in a file   |
      | exec  | Run a shell command      |

  Scenario: Tool definitions are included in the prompt
    When a prompt is built for the session
    Then the prompt has tools matching:
      | name  |
      | read  |
      | write |
      | edit  |
      | exec  |

  Scenario: Tool call is executed and result returned to LLM
    Given a file "hello.txt" exists with content "Hello, world!"
    And the following model responses are queued:
      | type      | name | arguments                  |
      | tool_call | read | {"filePath": "hello.txt"}  |
      | text      |      |                            |
    When the prompt is sent to the LLM
    Then the transcript has entries matching:
      | role       |
      | user       |
      | assistant  |
      | toolResult |
      | assistant  |
    And the tool result contains "Hello, world!"

  Scenario: Multiple tool calls in a conversation
    And the following model responses are queued:
      | type      | name  | arguments                                |
      | tool_call | write | {"filePath": "out.txt", "content": "hi"} |
      | tool_call | read  | {"filePath": "out.txt"}                  |
      | text      |       |                                          |
    When the prompt is sent to the LLM
    Then the transcript has 5 entries
    And the last tool result contains "hi"

  Scenario: Tool error is reported back to the LLM
    And the following model responses are queued:
      | type      | name | arguments                       |
      | tool_call | read | {"filePath": "nonexistent.txt"} |
      | text      |      |                                 |
    When the prompt is sent to the LLM
    Then the transcript has entries matching:
      | role       | isError |
      | toolResult | true    |

  Scenario: Unknown tool returns error to the LLM
    And the following model responses are queued:
      | type      | name       | arguments       |
      | tool_call | delete_all | {"path": "/"}   |
      | text      |            |                  |
    When the prompt is sent to the LLM
    Then the transcript has entries matching:
      | role       | isError |
      | toolResult | true    |
    And the tool result contains "unknown tool"

  Scenario: Agent without tools sends no tool definitions
    Given the following agents exist:
      | name     | model      | soul      |
      | no-tools | test-model | Just chat |
    When a prompt is built for the session
    Then the prompt has no tools

  Scenario: Tool calls work when streaming
    Given a file "hello.txt" exists with content "streamed content"
    And the following model responses are queued:
      | type      | name | arguments                  |
      | tool_call | read | {"filePath": "hello.txt"}  |
      | text      |      |                            |
    When the prompt is streamed to the LLM
    Then the tool result contains "streamed content"
    And response chunks arrive incrementally
