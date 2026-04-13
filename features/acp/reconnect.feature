@wip
Feature: ACP Proxy Reconnect
  When the remote server drops the WebSocket connection, the proxy
  notifies the client, retries with backoff, and resumes normal
  operation on reconnect. These scenarios use a background-thread
  proxy so the loopback can be poked mid-flight.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following agents exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |
    And config:
      | key                 | value    | #comment                            |
      | acp.proxy-transport | loopback | in-memory, supports simulated drops |

  Scenario: proxy notifies when connection is lost
    Given the acp proxy is running with "acp --remote ws://test/acp"
    And stdin receives:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      """
    Then the output has a JSON-RPC response for id 1:
      | key                    | value |
      | result.protocolVersion | 1     |
    When the loopback connection drops
    Then the output contains "Connection lost"
    And the output contains "Reconnecting"

  Scenario: proxy reconnects and resumes after drop
    Given the acp proxy is running with "acp --remote ws://test/acp"
    And stdin receives:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      """
    Then the output has a JSON-RPC response for id 1:
      | key                    | value |
      | result.protocolVersion | 1     |
    When the loopback connection drops
    And the loopback connection is restored
    Then the output contains "Reconnected"
    When stdin receives:
      """
      {"jsonrpc":"2.0","id":2,"method":"initialize","params":{"protocolVersion":1}}
      """
    Then the output has a JSON-RPC response for id 2:
      | key                    | value |
      | result.protocolVersion | 1     |

  Scenario: proxy gives up after max reconnect attempts
    Given config:
      | key                        | value | #comment            |
      | acp.proxy-max-reconnects   | 3     | low limit for tests |
    And the acp proxy is running with "acp --remote ws://test/acp"
    And stdin receives:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      """
    When the loopback connection drops permanently
    Then the output contains "Connection lost"
    And the stderr contains "gave up reconnecting"
    And the exit code is 1
