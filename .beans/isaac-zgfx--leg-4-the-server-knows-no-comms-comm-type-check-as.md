---
# isaac-zgfx
title: 'Leg 4 — the server knows no comms: comm-type check as an :isaac.config/check contribution; delete the six stale comm copies; telly replaced by a server-owned test comm'
status: todo
type: feature
priority: high
tags:
    - server
    - agent
    - comm
created_at: 2026-09-11T05:26:16Z
updated_at: 2026-09-11T05:39:44Z
parent: isaac-3q4m
---

Parent: isaac-3q4m (decision 6). Depends on leg 2 (the delivery worker must be a component before its copy goes).

Repo: **isaac-server** (deletes), **isaac-agent** (contributes the check).

1. `comm-validation-errors` (install.clj) becomes an `:isaac.config/check` contribution from the comm-owning side; the server's install/reload validate only via the generic check runner.
2. Delete isaac-server `src/isaac/comm/{protocol,memory,null,render,delivery/queue,delivery/worker}.clj` — June-14 copies that survived the "prune duplicate agent code" commit; the agent's namesakes are canonical and currently load first only by classpath order. The server's Comm protocol copy predates on-exhausted/on-bulletin/on-key.
3. The dev alias's `isaac.comm.telly` (agent repo fixture) is replaced by a test comm in isaac-server's spec-support.

Scenarios: none new — the unknown-comm-type validation scenario stays (now sourced from the check berth); the deletions and the telly replacement are one-time acceptance checks; logging.feature and the comm plumbing features stay green with the local test comm.

## Acceptance

Unknown-comm-type validation scenario green, sourced from the :isaac.config/check contribution; no isaac.comm.* namespaces under isaac-server/src (one-time check); no isaac.comm.telly in the server deps/bb.edn (one-time check); logging.feature and comm plumbing features green with the local test comm.

```
cd isaac-agent && bb features features/config/ && bb spec && bb ci
cd isaac-server && bb features && bb spec && bb ci
```
