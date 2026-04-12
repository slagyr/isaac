Feature: ACP Tool Calls
  Tool execution emits session/update notifications tracking state
  transitions (pending → completed).

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
    And the built-in tools are registered
    And the ACP client has initialized

  
  Scenario: Tool calls emit state updates
    Given the following model responses are queued:
      | tool_call | arguments              |
      | exec      | {"command": "echo hi"} |
    When the ACP client sends request 40:
      | key                   | value                       |
      | method                | session/prompt              |
      | params.sessionId      | agent:main:acp:direct:user1 |
      | params.prompt[0].type | text                        |
      | params.prompt[0].text | Run echo                    |
    Then the ACP agent sends notifications:
      | method         | params.update.sessionUpdate | params.update.status |
      | session/update | tool_call                   | pending              |
      | session/update | tool_call_update            | completed            |

  Scenario: Tool notifications include sessionId
    Given the following model responses are queued:
      | tool_call | arguments              |
      | exec      | {"command": "echo hi"} |
    When the ACP client sends request 41:
      | key                   | value                       |
      | method                | session/prompt              |
      | params.sessionId      | agent:main:acp:direct:user1 |
      | params.prompt[0].type | text                        |
      | params.prompt[0].text | Run echo                    |
    Then the ACP agent sends notifications:
      | method         | params.sessionId            | params.update.sessionUpdate |
      | session/update | agent:main:acp:direct:user1 | tool_call                   |
      | session/update | agent:main:acp:direct:user1 | tool_call_update            |

  Scenario: Tool call notifications include tool name and arguments
    Given the following model responses are queued:
      | tool_call | arguments              |
      | exec      | {"command": "echo hi"} |
    When the ACP client sends request 42:
      | key                   | value                       |
      | method                | session/prompt              |
      | params.sessionId      | agent:main:acp:direct:user1 |
      | params.prompt[0].type | text                        |
      | params.prompt[0].text | Run echo                    |
    Then the ACP agent sends notifications:
      | method         | params.update.sessionUpdate | params.update.toolName | params.update.input.command |
      | session/update | tool_call                   | exec                   | echo hi                     |
      | session/update | tool_call_update            |                        |                             |
