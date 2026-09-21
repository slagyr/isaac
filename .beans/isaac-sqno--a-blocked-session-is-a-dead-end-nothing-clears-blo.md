---
# isaac-sqno
title: 'A blocked session is a dead end: nothing clears :block and the failure counter cannot be reset'
status: todo
type: bug
priority: high
created_at: 2026-09-21T22:10:10Z
updated_at: 2026-09-21T22:10:10Z
---

Repo: **isaac-agent** (`src/isaac/drive/turn.clj`, `src/isaac/session/schema.clj`)
and **isaac-foundation** (the `sessions` CLI).

## What happens

When compaction fails `max-compaction-attempts` times in a row, `turn.clj:840`
writes a persistent marker on the session:

    :block {:reason :compaction-failed, :at "…"}

`conversation-blocked?` (`turn.clj:1018`) then short-circuits **every** turn via
`maybe-blocked-conversation!` — before compaction is attempted. So the retry
that would clear the condition can never run. The turn ends
`:ended-by :provider-unavailable` with a 5-minute retry that only re-reports the
block.

**Nothing in the codebase ever removes `:block`.** It is written in one place,
read in two, declared in `session/schema.clj:85`, and never cleared. The only
exit is an operator who knows to run `isaac sessions unset <id>.block`.

Worse, the counter that put it there cannot be reset:

    $ isaac sessions set isaac-work-3.compaction.consecutive-failures 0
    system-managed field: compaction.consecutive-failures

It resets only on a **successful** compaction (`turn.clj:864`). So a session that
has just been unblocked still sits at the maximum: one more failure re-latches
it immediately. There is no margin, and no way for an operator to restore any.

## What it cost (2026-09-21)

A provider outage (the Tonotop OAuth token was being overridden by a
process-wide `CLAUDE_CODE_OAUTH_TOKEN` in the launchd plist, so every call
authenticated as an exhausted account) made compaction fail three times on three
worker sessions. All three latched at 18:45. They stayed dead until a human
cleared them by hand.

Then it happened **again**: cleared at ~21:15, all three re-latched at
21:20–21:21, because each was still sitting at 3 failures and the outage had not
finished. And once more at 22:0x for `tono-work-1`. Three rounds of manual
`sessions unset` for one incident.

The visible symptom was not a block message. It was a hail retrying forever
against a blocked session — 136 identical `delivery-skipped` lines for a single
delivery (see isaac-udlg) — which reads as a stuck queue, not a blocked session.

## Change

A lever, and ideally self-healing:

1. **An operator lever.** `isaac sessions unblock <id>` that clears `:block`
   **and** resets `consecutive-failures` to 0, so the session has a full budget
   again rather than one attempt. `sessions unset <id>.block` half-does this
   today and leaves the trap armed.
2. **Self-healing.** A blocked session should retry compaction on a backoff
   rather than short-circuit forever — the block should suppress *work*, not
   suppress the recovery attempt. Clearing on the first success is enough.
3. **Visibility.** `sessions list` should mark a blocked session. Today the only
   way to find one is to grep `session.edn` files, and the pipeline just looks
   quiet.

## Acceptance

- `isaac sessions unblock <id>` clears `:block` and zeroes
  `consecutive-failures`; a session unblocked this way survives a single
  subsequent compaction failure without re-latching.
- A blocked session retries compaction on a backoff, and a successful compaction
  clears `:block` with no operator action.
- `isaac sessions list` shows blocked sessions distinctly.
- Spec coverage: a session at the failure ceiling, unblocked, does not re-latch
  on one failure; and a blocked session recovers on its own after a successful
  compaction.
