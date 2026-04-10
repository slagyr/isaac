Feature: Server Dev Reload
  In development mode, the server refreshes source code on every
  request so developers don't need to restart for every edit.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And config:
      | key        | value  |
      | log.output | memory |

  Scenario: Dev mode wraps the root handler with refresh
    Given config:
      | key | value |
      | dev | true  |
    And the Isaac server is running
    When a GET request is made to "/status"
    Then the log has entries matching:
      | level  | event                    |
      | :debug | :server/dev-reload-scan  |

  Scenario: Non-dev mode does not reload
    Given config:
      | key | value |
      | dev | false |
    And the Isaac server is running
    When a GET request is made to "/status"
    Then the log has no entries matching:
      | event                   |
      | :server/dev-reload-scan |

  Scenario: --dev CLI flag overrides config
    Given config:
      | key | value |
      | dev | false |
    When the server command is run with args "--dev"
    Then the log has entries matching:
      | level | event                    |
      | :info | :server/started          |
      | :info | :server/dev-mode-enabled |
