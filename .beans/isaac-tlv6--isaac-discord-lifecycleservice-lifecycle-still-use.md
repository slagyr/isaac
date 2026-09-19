---
# isaac-tlv6
title: 'isaac-discord: lifecycle/service_lifecycle still use retired server.port after tdlz http pin'
status: draft
type: bug
priority: high
tags:
    - ci
created_at: 2026-09-19T03:48:48Z
updated_at: 2026-09-19T03:48:48Z
---

Split from isaac-okw1. Genuine isaac-discord feature reds after pinning agent/http main (tdlz retired `:server`). lsz2 already called these "genuine / leave to their own beans." isaac-okw1 must not absorb them.

## Observed (isaac-discord bean/isaac-okw1 @ 5fff150, pins agent 76320fa / http d082206)

`bb spec` 52/0. `bb features` 68/6 fail / 3 pending:

- `lifecycle.feature` / `service_lifecycle.feature` still set `server.port` — http `d082206` retires `:server` (`:config/validation-error` path `server.port`). Sweep to `:http :port` (tdlz).
- `service_lifecycle` expected `:component/started` component discord, got `:module/activated`.
- `splitting.feature` third POST `body.content` expected `"echo"`, got nil.
- 3 pending episodes scenarios.

## Acceptance (draft — scenarios at promotion)

Named files green against current agent + http main. Do not recut pins. Do not absorb the MCP stare NPE.

```
cd isaac-discord && ISAAC_GIT=1 bb features features/lifecycle.feature features/service_lifecycle.feature features/splitting.feature
```
