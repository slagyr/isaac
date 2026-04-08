Feature: Server command
  Isaac can start an HTTP server that exposes API routes and reports service status.

  @speclj
  Scenario: Start the server with the server command
    Given config:
      | key         | value     |
      | server.host | 127.0.0.1 |
      | server.port | 3333      |
      | log.output  | memory    |
    When isaac is run with "server"
    Then the server started successfully
    And the log has entries matching:
      | level   | event           | host      | port |
      | :report | :server/started | 127.0.0.1 | 3333 |

  @speclj
  Scenario: Gateway is accepted as a command alias
    Given config:
      | key         | value     |
      | server.host | 127.0.0.1 |
      | server.port | 3333      |
      | log.output  | memory    |
    When isaac is run with "gateway"
    Then the server started successfully
    And the log has entries matching:
      | level   | event           | host      | port |
      | :report | :server/started | 127.0.0.1 | 3333 |

  @speclj
  Scenario: Server command uses gateway config as an alias
    Given config:
      | key          | value     |
      | gateway.host | 127.0.0.1 |
      | gateway.port | 3333      |
      | log.output   | memory    |
    When isaac is run with "server"
    Then the server started successfully
    And the log has entries matching:
      | level   | event           | host      | port |
      | :report | :server/started | 127.0.0.1 | 3333 |
