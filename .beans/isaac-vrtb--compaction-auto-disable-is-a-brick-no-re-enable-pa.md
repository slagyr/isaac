---
# isaac-vrtb
title: 'Compaction auto-disable is a brick: no re-enable path, no attention'
status: draft
type: bug
priority: high
created_at: 2026-08-31T14:15:35Z
updated_at: 2026-08-31T14:15:35Z
---

Repo: **isaac-agent** (compaction failure handling), possibly attention
config. Found 2026-08-31 during the isaac-work-1 recovery.

## Problem

After 5 consecutive compaction failures a session gets
`:compaction-disabled true` (session.edn) and `:session/compaction-stopped`
logs at warn. From then on EVERY turn hits the context guard and defers
`:context-exhausted` — forever. Nothing re-enables it, nothing notifies a
human: the session is a brick that only manual surgery fixes
(`isaac sessions unset <id>.compaction-disabled`). isaac-work-1 sat in this
state while hails retried into it on the 5-minute cadence, indistinguishable
from ordinary weather.

Contributing cause (separate beans): live estimator undercounts (pqjn not
deployed), so compaction's own summary requests exceeded the 500K wall and
failed 5×. But even with perfect counting, auto-disable needs an exit.

## Recovery that worked (Micah's procedure, now proven twice)

1. `isaac sessions unset <id>.compaction-disabled` (when disabled)
2. `isaac sessions set <id>.model claude-opus` — 200K window forces
   compaction; claude-cli has no 500K request wall; chunk planner produced
   3 chunks just under 200K each
3. one cheap housekeeping turn (`isaac hail send --session <id> --prompt …`)
4. `isaac sessions unset <id>.model`
Record this in a runbook/skill note regardless of the fix.

## Design questions for planning

- Re-enable triggers: on model/window change (behavior-resolved sees a
  different provider/window than the failures happened under — the opus
  trick, automated)? on operator unset only? time-based probe (retry one
  compaction every N hours)?
- Attention: `:session/compaction-stopped` should raise the attention comm
  (like auth weather does via attention/maybe-notify) — a bricked worker
  session is a page-worthy state.
- `consecutive-failures` is a system-managed field the CLI refuses to unset
  — re-enable must reset it internally.
- Interaction with isaac-bs5b (overflow = context-exhausted weather) and
  isaac-x2up: a compaction failure caused by provider overflow should maybe
  not count toward auto-disable at all once overflow defers as weather.

Draft until scenarios are written; sequence after x2up + the deploy train
(the estimator fixes remove the common cause).
