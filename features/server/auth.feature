Feature: Server-wide inbound HTTP auth
  Every inbound HTTP request goes through one shared bearer-token
  middleware. The token lives at `:server :auth :token`. Webhook hooks,
  ACP/gateway WebSocket upgrades, and direct routes all authenticate
  through the same gate.

  Loopback binds (127.0.0.1, ::1, anything `InetAddress/isLoopbackAddress`
  reports) are trusted without a token — a local attacker can already
  read the config and `.env` so middleware auth on loopback adds no
  protection. Non-loopback binds require `:server :auth :token` or the
  server refuses to start.

  Background:
    Given default Grover setup

  Scenario: A request with the configured Bearer token reaches the handler
    Given config:
      | server.host       | 0.0.0.0 |
      | server.auth.token | s3cr3t  |
    And the Isaac server is started
    When the client sends GET "/status" with header "Authorization: Bearer s3cr3t"
    Then the response status is 200

  Scenario: A request with no Authorization header is rejected
    Given config:
      | server.host       | 0.0.0.0 |
      | server.auth.token | s3cr3t  |
    And the Isaac server is started
    When the client sends GET "/status"
    Then the response status is 401
    And the response header "WWW-Authenticate" matches "Bearer.*"

  Scenario: A request with the wrong token is rejected
    Given config:
      | server.host       | 0.0.0.0 |
      | server.auth.token | s3cr3t  |
    And the Isaac server is started
    When the client sends GET "/status" with header "Authorization: Bearer wrong"
    Then the response status is 401

  Scenario: Loopback bind allows unauthenticated requests when no token is configured
    Given config:
      | server.host | 127.0.0.1 |
    And the Isaac server is started
    When the client sends GET "/status"
    Then the response status is 200

  Scenario: Loopback bind ignores a configured token (no auth required)
    Given config:
      | server.host       | 127.0.0.1 |
      | server.auth.token | s3cr3t    |
    And the Isaac server is started
    When the client sends GET "/status"
    Then the response status is 200

  Scenario: IPv6 loopback bind is treated the same as 127.0.0.1
    Given config:
      | server.host | ::1 |
    And the Isaac server is started
    When the client sends GET "/status"
    Then the response status is 200

  Scenario: Non-loopback bind without a token refuses to start
    Given config:
      | server.host | 0.0.0.0 |
    When the Isaac server is started
    Then the server failed to start
    And the log has entries matching:
      | level | event                  | message                                |
      | error | :server/auth-required  | .*:server :auth :token.*non-loopback.* |

  Scenario: Old :hooks :auth :token slot fails validation pointing to the new slot
    Given config:
      | server.host       | 0.0.0.0  |
      | server.auth.token | s3cr3t   |
      | hooks.auth.token  | leftover |
    When the config is loaded
    Then the config has validation errors matching:
      | key              | value                               |
      | hooks.auth.token | retired.*use :server :auth :token.* |

  Scenario: Token supports ${ENV_VAR} substitution from the state dir env
    Given the env var "ISAAC_AUTH_TOKEN" is set to "envt0ken"
    And config:
      | server.host       | 0.0.0.0             |
      | server.auth.token | ${ISAAC_AUTH_TOKEN} |
    And the Isaac server is started
    When the client sends GET "/status" with header "Authorization: Bearer envt0ken"
    Then the response status is 200
