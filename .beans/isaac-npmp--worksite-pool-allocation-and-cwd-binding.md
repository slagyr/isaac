---
# isaac-npmp
title: Worksite pool allocation and CWD binding
status: draft
type: feature
priority: normal
created_at: 2026-09-27T22:33:11Z
updated_at: 2026-09-27T22:45:05Z
blocked_by:
    - isaac-l3vb
    - isaac-ey7a
---

Likely repos: **isaac-worksite** and **isaac-agent**. Design: Micah + planner, 2026-09-27. Extends completed W1 (isaac-l3ps); does not assume this repo must survive the cutover.

## Contract to plan

- Contribute a worksite resource-pool type backed by named directories. Acquisition chooses and exclusively leases one free member, not the entire pool.
- The lease receipt binds `:session/cwd` before a new session is opened or a charge is built. An existing session pinned to another cwd is ineligible; never silently move its transcript or boot-file context.
- Operator lock/unlock, stale-holder recovery, and release on every turn outcome remain valid. Cross-process acquisition must be exclusive; a read-then-write race cannot claim the same member twice.
- The number of usable directory members, combined with session eligibility, determines concurrency. A request waits when no compatible pair is free and wakes when a member is released.
- Decide repo placement after the behavior lands: keep Worksite if its directory-specific policy has an independent surface, otherwise move that small implementation into Agent and retire the separate module in a follow-up.

## Scenario plan to review

1. Two members admit two turns; the third waits and takes the next released member.
2. A new session is opened at the selected cwd and reads that directory's boot context.
3. An existing pinned session selects only its compatible member.
4. An operator lock excludes its member while another free member remains usable.
5. Separate processes cannot acquire the same member simultaneously.

Draft until scenarios are committed and baselined. No database-pool implementation is in scope.
