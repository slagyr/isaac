Feature: ACP Remote Proxy
  `isaac acp --remote` bridges stdin/stdout to a remote ACP endpoint
  over a WebSocket connection. The WebSocket transport is abstracted
  so tests can wire client and server together in-process.

  Scenario: proxy forwards initialize and returns response
    Given an empty Isaac state directory "target/test-state"
    And the ACP proxy is connected via loopback
    And stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      """
    When isaac is run with "acp --remote ws://test/acp"
    Then the output has a JSON-RPC response for id 1:
      | key                    | value |
      | result.protocolVersion | 1     |
      | result.agentInfo.name  | isaac |
    And the exit code is 0

  Scenario: proxy forwards multiple requests in sequence
    Given an empty Isaac state directory "target/test-state"
    And the ACP proxy is connected via loopback
    And stdin is:
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

  Scenario: proxy streams notifications before final response
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
    And the following model responses are queued:
      | type | content | model |
      | text | Hello   | echo  |
    And the ACP proxy is connected via loopback
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
    Given the ACP proxy is connected via loopback
    And stdin is empty
    When isaac is run with "acp --remote ws://test/acp"
    Then the exit code is 0

  Scenario: proxy fails with clear error when connection is refused
    Given stdin is empty
    When isaac is run with "acp --remote ws://localhost:9999/acp"
    Then the stderr contains "could not connect"
    And the exit code is 1
