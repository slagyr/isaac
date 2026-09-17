---
# isaac-s9e3
title: HTTP auth token takes effect on hot reload without a restart
status: todo
type: bug
priority: high
tags:
    - server
    - auth
    - hot-reload
created_at: 2026-09-17T17:19:26Z
updated_at: 2026-09-17T17:19:26Z
---

Repo: **isaac-http** (local checkout `isaac-server`). Production HTTP component is `src/isaac/http/component/http.clj`; wrap-auth is `src/isaac/http/http.clj`.

## Why

2026-09-17 on zanebot: `isaac config set server.auth.token` hot-reloaded (`:config/reloaded` at 08:57) but inbound HTTP still accepted the old token until process restart at 09:02. Health hooks from iphone171 got 202 in that window; nightbird `/cli` 401'd the moment the new process bound the new token.

## Decisions (2026-09-17, Micah)

1. **Token is live.** `:server :auth :token` takes effect on the next request after reload. No restart. Clean cutover — old bearer 401s, new bearer 200s. No dual-token grace.
2. **Host/port stay restart-only.** Bind is a socket; token is per-request policy. Do not group them. Related draft: isaac-03wy (host/port silent ignore).
3. **Burst follows the token.** Same `cfg-fn` seam; no extra scenario.
4. **Honest reload.** `:config/reloaded` means the new token is in force.

## Root cause

`wrap-auth` already reads `cfg-fn` per request. Production wires `:cfg-fn (constantly config)` at HTTP component start, capturing the boot map. Reload updates the snapshot and reconciles hail/hooks/cron; it does not rebuild the listener. Feature harness already uses `(fn [] (loader/snapshot ...))`, so in-process requests would hide the bug. These scenarios bind a real port on `0.0.0.0` so they go through httpkit.

## Implementation

Point production `cfg-fn` at `loader/snapshot` (or `isaac.http.app/current-config`) the same way the feature harness does. Do not rebind host/port. Add a unit spec that after snapshot change, wrap-auth / the component's `cfg-fn` sees the new token without `protocol/start`.

## Acceptance

Scenarios in `features/server/auth.feature` (commit 04f0e59), `@wip` on each:

- `features/server/auth.feature:86` — Old bearer is rejected after a token reload
- `features/server/auth.feature:99` — New bearer is accepted after a token reload

```
cd isaac-http   # or isaac-server checkout
bb features features/server/auth.feature:86 features/server/auth.feature:99
bb spec spec/isaac/http
bb ci
```

Done when both `@wip` tags are gone and those commands are green.

## Non-goals

Live rebind of `:server :host` / `:server :port`. Dual-token overlap. Burst-specific scenarios. Stopping `isaac config set` from logging the token value in `cli.log` (separate leak).
