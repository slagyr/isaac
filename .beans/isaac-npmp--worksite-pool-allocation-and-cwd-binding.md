---
# isaac-npmp
title: Worksite pool allocation and CWD binding
status: draft
type: feature
priority: normal
created_at: 2026-09-27T22:33:11Z
updated_at: 2026-09-27T23:45:55Z
parent: isaac-q3u3
blocked_by:
    - isaac-l3vb
    - isaac-ey7a
    - isaac-i5lv
---

Likely repos: **isaac-worksite** and **isaac-agent**. Design: Micah + planner, 2026-09-27. Extends completed W1 (isaac-l3ps); does not assume this repo must survive the cutover.

## Contract to plan

- Contribute a worksite resource-pool type (isaac-ey7a contract) backed by named directories. Acquisition chooses and exclusively leases one free member, not the entire pool. `try-acquire` never blocks: a free member or `:busy`.
- The lease receipt binds `:session/cwd` for **that turn** before its charge is built. Sessions are independent of directories: any session the session pattern selects can run in any free member, and the same session may run its next turn in a different member.
- Operator lock/unlock, stale-holder recovery (via release-identity reconciliation on restart), and release on every turn outcome remain valid. Cross-process acquisition must be exclusive; a read-then-write race cannot claim the same member twice.
- The number of free directory members, together with free sessions, determines concurrency. A request waits when either is exhausted and wakes when a member is released.
- Decide repo placement after the behavior lands: keep Worksite if its directory-specific policy has an independent surface, otherwise move that small implementation into Agent and retire the separate module in a follow-up.

## Scenario plan to review

1. Two members admit two turns; the third waits and takes the next released member.
2. A turn runs at its leased member's cwd and reads that directory's boot context.
3. One session's consecutive turns run in whichever member is free, not a fixed one.
4. An operator lock excludes its member while another free member remains usable.
5. Separate processes cannot acquire the same member simultaneously.

Draft until scenarios are committed and baselined. No database-pool implementation is in scope.
