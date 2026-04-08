Feature: Server startup command
  Isaac can be started as an HTTP server via the serve command.

  Background:
    Given config:
      | key        | value  |
      | log.output | memory |

  @speclj
  Scenario: serve command logs startup with port
    When the serve command is run on port 9876
    Then the log has entries matching:
      | level | event            | port |
      | :info | :server/started  | 9876 |

  @speclj
  Scenario: gateway is an alias for serve
    When the gateway command is run on port 9877
    Then the log has entries matching:
      | level | event            | port |
      | :info | :server/started  | 9877 |
