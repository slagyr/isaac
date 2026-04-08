Feature: Server status endpoint
  The HTTP server exposes a /status endpoint for health monitoring.

  @speclj
  Scenario: GET /status returns 200 with JSON services
    Given the Isaac server is running
    When a GET request is made to "/status"
    Then the response status is 200
    And the response body has "status" equal to "ok"
    And the response body has a "services" key
