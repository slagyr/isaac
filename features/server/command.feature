Feature: Server startup command
  Isaac can be started as an HTTP server via the server command.

  Background:
    Given config:
      | key        | value  |
      | log.output | memory |

  @speclj
  Scenario: server command logs startup with port
    When the server command is run on port 9876
    Then the log has entries matching:
      | level | event           | port |
      | :info | :server/started | 9876 |

  @speclj
  Scenario: gateway is an alias for server
    When the gateway command is run on port 9877
    Then the log has entries matching:
      | level | event           | port |
      | :info | :server/started | 9877 |

  @speclj
  Scenario: gateway.port config key is used as server port
    Given config:
      | key          | value  |
      | log.output   | memory |
      | gateway.port | 9878   |
    When the server command is run without a port flag
    Then the log has entries matching:
      | level | event           | port |
      | :info | :server/started | 9878 |
