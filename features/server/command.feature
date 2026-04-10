Feature: Server startup command
  Isaac can be started as an HTTP server via the server command.

  Background:
    Given config:
      | key        | value  |
      | log.output | memory |

  Scenario: server command logs startup with host and port
    When the server command is run on port 9876
    Then the log has entries matching:
      | level | event           | port | host    |
      | :info | :server/started | 9876 | 0.0.0.0 |

  Scenario: gateway is an alias for server
    When the gateway command is run on port 9877
    Then the log has entries matching:
      | level | event           | port | host    |
      | :info | :server/started | 9877 | 0.0.0.0 |

  Scenario: gateway.port config key is used as server port
    Given config:
      | key          | value  |
      | log.output   | memory |
      | gateway.port | 9878   |
    When the server command is run without a port flag
    Then the log has entries matching:
      | level | event           | port | host    |
      | :info | :server/started | 9878 | 0.0.0.0 |

  # Port 6674 = first four digits of Newton's gravitational constant
  # G = 6.6743 × 10⁻¹¹ N·m²/kg²
  Scenario: Default port is 6674 when no port is configured
    When the server command is run without a port flag
    Then the log has entries matching:
      | level | event           | port | host    |
      | :info | :server/started | 6674 | 0.0.0.0 |
