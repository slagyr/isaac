---
# isaac-r209
title: Frequencies pick a busy session over an idle one (hails pile onto the running worker)
status: draft
type: bug
priority: high
created_at: 2026-10-02T14:43:02Z
updated_at: 2026-10-02T14:43:02Z
---

Found 2026-10-02 on zanebot: three hails to band `isaac-work` (isaac-izc1, isaac-o13p, isaac-ixcm), sent seconds apart, all resolved to session `isaac-work-1`. The first started a turn; the other two arrived while it was busy and were folded into the running turn as extra messages (records `finished` immediately, `turn/waiting` → woke), while `isaac-work-2` and `isaac-work-3` (same crew/tags) sat idle. The planner had to re-hail them session-direct.

Micah: hail used to prefer idle sessions; this regressed when the turn queue moved into agent — frequencies now resolve to a session in `isaac.agent.frequencies` (match by session-tags/crew, tiebreak `:prefer :recent`), which ignores whether the session is busy.

## Wanted
When several sessions match a frequencies map, prefer a session with no running or queued turn; only when all are busy fall back to the existing tiebreak (and then queue, not merge into a running turn, for a separate request — confirm with Micah whether separate hails should ever coalesce).

## Acceptance (scenarios TBD)
- Two sessions match; one has a running turn → the turn goes to the idle one.
- All matching sessions busy → existing tiebreak, and the request waits its turn.
