Feature: Server request logging
  The HTTP server logs request lifecycle events as structured EDN entries.

  Background:
    Given config:
      | key        | value  |
      | log.output | memory |
    And the Isaac server is running

  @speclj
  Scenario: Successful request lifecycle is logged at debug
    When a GET request is made to "/status"
    Then the log has entries matching:
      | level  | event                    | uri     |
      | :debug | :server/request-received | /status |
      | :debug | :server/response-sent    | /status |

  @speclj
  Scenario: Failed request is logged at error with context
    When a GET request is made to "/error"
    Then the log has entries matching:
      | level  | event                   | uri    | status |
      | :error | :server/request-failed  | /error | 500    |
