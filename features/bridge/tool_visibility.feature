Feature: Crew tools reach every comm path
  A crew's :tools.allow is the source of truth for which tools are
  offered to the model on every turn. Every in-tree channel that
  drives a turn (stdio ACP, HTTP/WebSocket ACP, prompt) must surface
  the same tool set. If a crew has no :tools section, no tools are
  offered — regardless of channel.

  Module comms (Discord, iMessage, …) verify this contract in their
  own repos against the same `the prompt has tools:` assertion.

  Background:
    Given default Grover setup
    And the crew "main" allows tools: "read,write,exec"

  Scenario: stdio ACP offers the crew's configured tools
    Given the following sessions exist:
      | name |
      | s1   |
    And stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"s1","prompt":[{"type":"text","text":"hi"}]}}
      """
    When isaac is run with "acp"
    Then the prompt has tools:
      | name  |
      | read  |
      | write |
      | exec  |

  @slow
  Scenario: HTTP/WebSocket ACP offers the crew's configured tools
    Given the following sessions exist:
      | name |
      | s1   |
    And config:
      | key         | value |
      | server.port | 0     |
    And the Isaac server is started
    And stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"s1","prompt":[{"type":"text","text":"hi"}]}}
      """
    When isaac is run with "acp --remote ws://localhost:${server.port}/acp"
    Then the prompt has tools:
      | name  |
      | read  |
      | write |
      | exec  |

  Scenario: prompt command offers the crew's configured tools
    When isaac is run with "prompt hi"
    Then the prompt has tools:
      | name  |
      | read  |
      | write |
      | exec  |

  Scenario: a crew with no :tools section still gets zero tools over every comm
    Given the isaac EDN file "config/crew/main.edn" exists with:
      | path | value |
      | model | grover |
      | soul | Marvin. Paranoid droid. |
    When isaac is run with "prompt hi"
    Then the prompt has 0 tools
