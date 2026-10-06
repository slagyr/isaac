---
# isaac-x3g4
title: A worksite acquisition that fails leaves its lock behind; one Foreman turn leased all four members
status: todo
type: bug
priority: high
created_at: 2026-10-06T20:23:24Z
updated_at: 2026-10-06T20:23:24Z
---

Likely repos: **isaac-agent** (turn worker) and/or **isaac-worksite** (lock guard). Found by
Foreman pilot 1 (isaac-8uno on zanebot), 2026-10-06. No planner scenario: reproduce first.

## What happened

One Foreman work turn (`0fa9c4e4`, pool `isaac-worksites`, 4 members) was submitted from inside
Prowl's `foreman__signal` tool call; Foreman then called `worker/tick!` inline. All four
worksite locks were written by the server (pid 28583) within 0.4 ms of each other:

    work-1 19:05:15.583023  work-2 .583196  work-3 .583304  work-4 .583395

No `:turn.queue/woke` was ever logged for the turn, and no warn/error. Every later tick found
the pool busy and kept it held. `turns drop` finished the record but freed nothing; the locks
are still on disk (`~/.isaac/worksites/*.lock`, holder `bean-isaac-8uno`). A restart frees them
(the owner pid dies → stale → stolen).

## What we know

- 100 µs apart in one call means ONE `try-acquire` walked all members: each
  `lock/acquire-turn!` wrote its lock yet reported not-ok, so `some` moved on and the pool
  answered `:busy`. `with-guard`'s RealFs branch turns ANY exception into "busy" silently
  (`(catch Exception _ busy)`), so a throw after `write-lock!` (or from `.release`) would look
  exactly like this.
- The pool and lock work in isolation on a real filesystem (planner check, 2026-10-06,
  including an interrupted thread). The feature suite runs on MemFs, which skips the RealFs
  guard branch entirely.

## Acceptance

- A spec reproduces the leak (real fs; whatever throws inside the guard), then passes.
- A worksite acquisition never reports busy/failed while leaving a lock it wrote; the swallowed
  exception is logged (`:worksite/…` warn with the message).
- Anything that throws between lease acquisition and the turn claim in the queue worker releases
  the leases it took (`claim-and-start!` / `admit!`).
- isaac-worksite and isaac-agent features stay green.
