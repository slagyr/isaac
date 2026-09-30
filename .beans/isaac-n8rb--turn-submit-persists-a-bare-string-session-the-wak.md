---
# isaac-n8rb
title: turn submit persists a bare-string :session; the wake re-resolution reads it as characters
status: todo
type: bug
priority: high
created_at: 2026-09-30T18:14:06Z
updated_at: 2026-09-30T18:14:06Z
---

Found 2026-09-30 by the foreman restructure (isaac-sb9f). `isaac.agent.turn.submit/submit!` coerces a bare-string `:session` to a vector only for its own submit-time `resolve-session-targets` check, but persists the ORIGINAL frequencies into the durable queue record. The worker's wake-time re-resolution reads that raw `:frequencies` and calls `resolve-session-targets` without the coercion: `(first "lamp-room")` is \\l, so a session named "l" gets created and the turn crashes ("Don't know how to create ISeq from: java.lang.Character").

## Acceptance
- Red first: an agent spec submits with `{:session "lamp-room"}`, ticks, and the turn runs on session lamp-room (fails on current main).
- Fix in agent: the persisted record carries the normalized frequencies (or wake re-applies the same normalization). One normalization, used by both paths.
- Foreman's `normalize-frequencies` workaround (bean/isaac-sb9f worktree) can be dropped afterwards.
- isaac-agent `bb ci` + `bb jvm-spec` green. Ungated.
