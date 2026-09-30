---
# isaac-8evx
title: Retry wake of a pool-held turn resolves its resource pool as unknown
status: todo
type: bug
priority: high
created_at: 2026-09-30T18:14:06Z
updated_at: 2026-09-30T18:14:06Z
---

Found 2026-09-30 by the foreman restructure (isaac-sb9f). A turn held by a closed resource pool: the first wake correctly resolves the pool (known, closed). A later wake via `foreman retry` (which loads a fresh config snapshot and calls `isaac.agent.turn.worker/tick!`) resolves the same pool as `:unknown-resource-pool`, with the same pool file on disk. Foreman scenarios in features/foreman/turn_action.feature: "a refused submission stays pending…" and "a retry after Foreman lost the request id…" fail on agent 123d718 and pass on the old pins. Root cause not yet traced (resource-pool resolution on the wake path vs the config snapshot tick! uses).

## Acceptance
- Red first: an agent-level scenario/spec where a pool-held turn is woken twice (second wake from a freshly loaded config, as a CLI would) and the pool resolves as known both times.
- Fix in agent; no sleeps.
- isaac-foreman bean/isaac-sb9f goes green on those two scenarios when pinned to the fixed agent.
- isaac-agent `bb ci` + `bb jvm-spec` green. Ungated.
