---
# isaac-n8uv
title: 'agent session steps: stale :turn-future blocks transcript checks on a still-parked session'
status: in-progress
type: bug
priority: high
created_at: 2026-09-30T17:58:47Z
updated_at: 2026-09-30T19:23:46Z
---

Found 2026-09-30 by the worksite restructure (isaac-6uou). isaac-agent's test support (`isaac.agent.session.session-steps`, shipped in isaac-agent-spec): `user-sends-on-session` now sets the single `:turn-future` slot only when nothing is already parked. After a second session's turn ends, `:turn-future` still points at a different, intentionally parked session's future, so asserting the first session's transcript (`await-turn!` / `await-transcript-turn!`) blocks 30s and fails.

Repro: isaac-worksite branch `bean/isaac-6uou` (worktree ../isaac-worksite-isaac-6uou), `features/worksite/lock.feature` "two members run two turns; a third waits…": 1/8 fails on agent 123d718, passes 8/8 in ~3.6s on the old pins.

## Acceptance
- Red first: an isaac-agent spec/feature where two sessions each send, one is parked, the other's turn ends, and the other session's transcript check returns promptly (fails on current main).
- Fix: turn futures are tracked per session (or the await resolves the right session's future); no sleeps, no longer timeouts.
- isaac-worksite `bean/isaac-6uou` goes 8/8 on `bb jvm-features` pinned to the fixed agent.
- isaac-agent `bb ci` + `bb jvm-spec` green.

Ungated; planner verifies.
