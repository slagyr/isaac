---
# isaac-pp3q
title: 'isaac-cli-proxy @slow token-reject: spawned cli-server must honor http.auth'
status: draft
type: bug
priority: high
tags:
    - ci
created_at: 2026-09-19T19:36:55Z
updated_at: 2026-09-19T19:36:55Z
---

Split from isaac-ox53. Pre-existing on isaac-cli-proxy origin/main independently of the ox53 pin bump.

`features/integration.feature` @slow "the server rejects a remote command without a valid token" is red on origin/main@3cb4198 and was already red on 1f96845 (x2lp verify). The scenario already says `http.auth.token` (tdlz); the spawned cli-server classpath still pins http `ad4ba5d` and reads `:server :auth :token`, so the server starts unauthenticated.

isaac-ox53 must not absorb this. Do not recut pins on hail/claude-code.

## Observed

isaac-cli-proxy `bb features-slow` 4 examples, 1 failure on origin/main (token-reject only). After ox53 pins, the first remote-command scenario also went red because it still set `server.port` — that recut belongs to ox53 Exceptions, not here.

## Acceptance (draft — scenarios at promotion)

Spawned cli-server honors `http.auth.token`. Scenario green as written (`token rejected`, exit 77). Do not weaken the Then.

```
cd isaac-cli-proxy && ISAAC_GIT=1 bb features-slow features/integration.feature
```
