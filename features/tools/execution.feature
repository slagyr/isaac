Feature: Tool execution logging
  Tool execution events are logged as structured entries for observability.

  Background:
    Given config:
      | key        | value  |
      | log.output | memory |
    And the built-in tools are registered

  Scenario: Successful tool execution is logged at debug
    When tool "read" is executed with:
      | filePath | /etc/hosts |
    Then the log has entries matching:
      | level  | event       | tool |
      | :debug | :tool/start | read |

  Scenario: Tool failure is logged at error with tool context
    When tool "read" is executed with:
      | filePath | /no/such/path/that/exists |
    Then the log has entries matching:
      | level  | event                | tool |
      | :error | :tool/execute-failed | read |

  @wip
  Scenario: Nil tool result is treated as an error
    Given a tool "nil-tool" that returns nil is registered
    When tool "nil-tool" is executed with:
      | arg | value |
    Then the tool result should indicate an error
    And the log has entries matching:
      | level  | event                | tool     |
      | :error | :tool/execute-failed | nil-tool |
