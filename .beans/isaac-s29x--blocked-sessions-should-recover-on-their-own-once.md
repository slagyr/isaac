---
# isaac-s29x
title: Blocked sessions should recover on their own once the provider comes back
status: todo
type: bug
priority: normal
created_at: 2026-09-21T22:17:55Z
updated_at: 2026-09-21T22:17:55Z
---

Repo: **isaac-agent** (`src/isaac/drive/turn.clj`).
Split out of isaac-sqno, which carries the incident detail and ships the
operator lever. This bean removes the need to pull that lever.

## Problem

`maybe-blocked-conversation!` (`turn.clj:1048`) short-circuits every turn while
`:block` is set — **before** compaction is attempted. So the retry that would
clear the condition can never run. The turn ends `:ended-by
:provider-unavailable` with a 5-minute retry that only re-reports the block,
forever.

The block was caused by compaction failing during a provider outage. Once the
outage passes, nothing notices: the session stays dead until a human intervenes.

## Change

The block should suppress **work**, not suppress **recovery**. A blocked session
should retry compaction on a backoff; a successful compaction clears `:block`
(and already zeroes `consecutive-failures` at `turn.clj:864`).

## Acceptance

- A session blocked by `:compaction-failed` retries compaction on a backoff
  rather than short-circuiting indefinitely.
- A successful retry clears `:block` with no operator action, and the session
  takes its next turn normally.
- A failing retry does not spam: the backoff widens, and the attempt is logged
  once per state change (same discipline as isaac-udlg).
- Spec coverage: blocked session + provider restored → recovers unattended.
