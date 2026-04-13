Feature: ACP Remote Proxy
  `isaac acp --remote` bridges stdin/stdout to a remote ACP endpoint
  over a WebSocket connection. The WebSocket transport is configured
  via `acp.proxy-transport` so tests use an in-memory loopback.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias   | model    | provider | contextWindow |
      | grover  | echo     | grover   | 32768         |
      | grover2 | echo-alt | grover   | 16384         |
    And the following agents exist:
      | name  | soul              | model  |
      | main  | You are Isaac.    | grover |
      | ketch | You are a pirate. | grover |
    And config:
      | key                 | value    | #comment                            |
      | acp.proxy-transport | loopback | in-memory, supports simulated drops |

  Scenario: proxy forwards initialize and returns response
    Given config:
      | key        | value  |
      | log.output | memory |
    And stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      """
    When isaac is run with "acp --remote ws://test/acp"
    Then the output has a JSON-RPC response for id 1:
      | key                    | value |
      | result.protocolVersion | 1     |
      | result.agentInfo.name  | isaac |
    And the log has entries matching:
      | level  | event                  |
      | :debug | :acp-proxy/connected   |
      | :debug | :acp-ws/initialize     |
      | :debug | :acp-proxy/initialize  |
    And the exit code is 0

  Scenario: proxy forwards multiple requests in sequence
    Given stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      {"jsonrpc":"2.0","id":2,"method":"session/new","params":{}}
      """
    When isaac is run with "acp --remote ws://test/acp"
    Then the output has a JSON-RPC response for id 1:
      | key                    | value |
      | result.protocolVersion | 1     |
    And the output has a JSON-RPC response for id 2:
      | key              | value |
      | result.sessionId | #*    |
    And the exit code is 0

  Scenario: proxy session has tools available
    Given the built-in tools are registered
    And agent "main" has sessions:
      | key                         |
      | agent:main:acp:direct:user1 |
    And the following model responses are queued:
      | tool_call | arguments              |
      | exec      | {"command": "echo hi"} |
    And stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"agent:main:acp:direct:user1","prompt":[{"type":"text","text":"run echo"}]}}
      """
    When isaac is run with "acp --remote ws://test/acp"
    Then the output lines contain in order:
      | pattern          |
      | tool_call        |
      | tool_call_update |
      | end_turn         |
    And the exit code is 0

  Scenario: proxy streams notifications before final response
    Given agent "main" has sessions:
      | key                         |
      | agent:main:acp:direct:user1 |
    And the following model responses are queued:
      | type | content | model |
      | text | Hello   | echo  |
    And stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"agent:main:acp:direct:user1","prompt":[{"type":"text","text":"hi"}]}}
      """
    When isaac is run with "acp --remote ws://test/acp"
    Then the output lines contain in order:
      | pattern        |
      | session/update |
      | end_turn       |
    And the exit code is 0

  Scenario: proxy exits cleanly on stdin EOF
    Given stdin is empty
    When isaac is run with "acp --remote ws://test/acp"
    Then the exit code is 0

  Scenario: tool notifications arrive before the final response
    Given the built-in tools are registered
    And agent "main" has sessions:
      | key                         |
      | agent:main:acp:direct:user1 |
    And the following model responses are queued:
      | tool_call | arguments              |
      | exec      | {"command": "echo hi"} |
    And the loopback holds the final response
    And the acp proxy is running with "acp --remote ws://test/acp"
    And stdin receives:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"agent:main:acp:direct:user1","prompt":[{"type":"text","text":"run it"}]}}
      """
    Then the output eventually contains "tool_call"
    And the output does not contain "end_turn"
    When the loopback releases the final response
    Then the output eventually contains "end_turn"

  Scenario: proxy fails with clear error when connection is refused
    Given config:
      | key                 | value | #comment                    |
      | acp.proxy-transport |       | real WS, no loopback        |
    And stdin is empty
    When isaac is run with "acp --remote ws://localhost:9999/acp"
    Then the stderr contains "could not connect"
    And the exit code is 1

  Scenario: --model is forwarded to the remote server
    Given stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      """
    When isaac is run with "acp --remote ws://test/acp --model grover2"
    Then the output has a JSON-RPC response for id 1:
      | key                       | value    |
      | result.agentInfo.model    | echo-alt |
      | result.agentInfo.provider | grover   |
    And the exit code is 0

  Scenario: --agent is forwarded to the remote server
    Given stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      {"jsonrpc":"2.0","id":2,"method":"session/new","params":{}}
      """
    When isaac is run with "acp --remote ws://test/acp --agent ketch"
    Then the output has a JSON-RPC response for id 2:
      | key              | value |
      | result.sessionId | #*    |
    And the exit code is 0

  Scenario: --resume is forwarded to the remote server
    Given agent "main" has sessions:
      | key                            | updatedAt           |
      | agent:main:acp:direct:recent   | 2026-04-12T15:00:00 |
    And stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      {"jsonrpc":"2.0","id":2,"method":"session/new","params":{}}
      """
    When isaac is run with "acp --remote ws://test/acp --resume"
    Then the output has a JSON-RPC response for id 2:
      | key              | value                        |
      | result.sessionId | agent:main:acp:direct:recent |
    And the exit code is 0
