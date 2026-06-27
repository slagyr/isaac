---
# isaac-3e1s
title: 'Remote CLI integration: batch round-trip + auth end-to-end'
status: completed
type: feature
priority: normal
created_at: 2026-06-27T15:16:53Z
updated_at: 2026-06-27T16:28:40Z
parent: isaac-ec9q
---

The one @slow end-to-end feature for the remote-CLI epic (isaac-ec9q): a REAL Isaac server (cli-server module) on an ephemeral port, driven by the REAL `isaac remote` proxy (cli-proxy). Proves the live wiring the isolated suites stub out (cov2 = handler only; 7p1i = stub server). M1 children cov2 + 7p1i are DONE, so scenarios 1-3 are buildable now. (Scenario 4, acp-over-/cli, is the M3 marquee — separate bean, needs interactive streaming first.)

## Home & harness
- Lives in **isaac-cli-proxy** (`features/integration.feature`) — the proxy is the driver (`isaac is run with "remote …"`) and tops the dep chain.
- Test deps include ALL repos (cli-server, server, agent, foundation) so the classpath is full — no module-import juggling. Activate the berths needed:
  - `/cli` route: include cli-server's manifest in the server manifest index the test boots (like marigold_server's baseline index) so the route berth mounts it on `the Isaac server is started`.
  - `remote` command: register directly in the step ns via cli-registry/register! (like acp's steps do for acp/chat).
- `@slow`. Unblocked (isaac-opc4 resolved).

## Scenarios

  Background:
    Given default Grover setup

  @slow
  Scenario: a remote command runs on the server and streams back
    Given config:
      | server.host | 0.0.0.0 |
      | server.port | 0       |
    And the Isaac server is started
    When isaac is run with "remote ws://localhost:${server.port}/cli -- --version"
    Then the stdout contains "isaac"
    And the exit code is 0

  @slow
  Scenario: the server rejects a remote command without a valid token
    Given config:
      | server.host       | 0.0.0.0   |
      | server.port       | 0         |
      | server.auth.token | secret123 |
    And the Isaac server is started
    When isaac is run with "remote ws://localhost:${server.port}/cli -- --version"
    Then the stderr contains "authentication failed"
    And the exit code is 1

  @slow
  Scenario: a valid token authenticates the remote command
    Given config:
      | server.host       | 0.0.0.0   |
      | server.port       | 0         |
      | server.auth.token | secret123 |
    And the Isaac server is started
    When isaac is run with "remote ws://localhost:${server.port}/cli --token secret123 -- --version"
    Then the stdout contains "isaac"
    And the exit code is 0

## DoD
- features/integration.feature green (3 scenarios) in isaac-cli-proxy via dev-local.
- Real server boot + real proxy round-trip; auth enforced (reject without token, accept with).
- Mirrors acp_websocket.feature's real-boot pattern.

## Handoff (work-2)
- Pushed `isaac-cli-proxy` @ `2da864a`. `bb ci` green (spec + features + features-slow).
- `integration_steps` declares cli-server `:modules` + `:inject-module-index` in **remote CLI command is registered** (after Grover setup — `before-scenario` was too early; `g/reset!` wiped inject).
- Scenario 1 uses `server.host 127.0.0.1` (loopback) for token-less bind; `0.0.0.0` without `server.auth.token` refuses to start per `isaac.server.app/auth-required?`.
- `feature_bootstrap` drops duplicate `config:` / `default Grover setup` when server steps load. `bb features-slow` uses `-M:dev-local:features:features-slow`.

## Verification

Verified on fetched GitHub heads:

- `isaac-cli-proxy` `2da864a9a0f1ea31f1ca36e7ff92848af3a49f36`
- `isaac-cli-server` `96df1ccaa56e4c9dd6d067ef1debd693ec029ed2`
- `isaac-server` `eb51cc48b8964dabb678086ac36051a86d94c03a`
- `isaac-agent` `115ed52d18d481fec709f6cbb8460cafc9ffcd82`
- `isaac-foundation` `a8344457b8b187738092072e92e0776a0128c721`

Proofs were green in a sibling worktree layout matching the proxy's
`../isaac-*` `:dev-local` overrides:

- `bb ci` in `isaac-cli-proxy`:
  - `bb spec` -> `6 examples, 0 failures, 10 assertions`
  - `bb features` -> `5 examples, 0 failures, 13 assertions`
  - `bb features-slow` -> `3 examples, 0 failures, 6 assertions`

That covers the real server boot, real `/cli` route mounting, real `isaac remote`
proxy round-trip, and token auth rejection/acceptance end-to-end.
