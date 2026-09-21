---
# isaac-sqno
title: 'A blocked session is a dead end: nothing clears :block and the failure counter cannot be reset'
status: todo
type: bug
priority: high
created_at: 2026-09-21T22:10:10Z
updated_at: 2026-09-21T22:10:10Z
---

Repo: **isaac-foundation** (the `sessions` CLI) + **isaac-agent**
(`src/isaac/session/schema.clj` — `consecutive-failures` is system-managed).

## What happens

When compaction fails `max-compaction-attempts` times in a row, `turn.clj:840`
writes a persistent marker on the session:

    :block {:reason :compaction-failed, :at "…"}

**Nothing in the codebase ever removes it.** It is written in one place, read in
two (`turn.clj:1018`, `1048`), declared in `session/schema.clj:85`, and never
cleared. The only exit is an operator who already knows to run
`isaac sessions unset <id>.block`.

And that only half-works, because the counter that caused the block cannot be
reset:

    $ isaac sessions set isaac-work-3.compaction.consecutive-failures 0
    system-managed field: compaction.consecutive-failures

It resets only on a **successful** compaction (`turn.clj:864`). So a session
unblocked by hand is still sitting at the ceiling: the very next failure
re-latches it. There is no margin and no way for an operator to restore any.

## What it cost (2026-09-21)

A provider outage — the Tonotop OAuth token was being overridden by a
process-wide `CLAUDE_CODE_OAUTH_TOKEN` in the launchd plist, so every call
authenticated as an exhausted account — made compaction fail three times on
three worker sessions. All three latched at 18:45.

`sessions unset <id>.block` cleared them at ~21:15. All three re-latched at
21:20–21:21, because each was still at 3 failures and the outage had not
finished. `tono-work-1` latched a third time at ~22:0x. **Three rounds of manual
intervention for one incident**, and each round reopened the session with zero
margin.

The visible symptom was never a block message. It was a hail retrying forever
against a blocked session (isaac-udlg), which reads as a stuck queue.

## Change

`isaac sessions unblock <id>`: clear `:block` **and** reset
`consecutive-failures` to 0, so the session resumes with a full budget rather
than one attempt. This is the operator lever; `sessions unset <id>.block` is
the half-measure it replaces.

## Acceptance

- `isaac sessions unblock <id>` clears `:block` and zeroes
  `consecutive-failures`.
- A session unblocked this way survives a single subsequent compaction failure
  without re-latching (it re-latches only after the full
  `max-compaction-attempts` again).
- Unblocking a session with no `:block` is a no-op that exits 0, not an error.
- Spec coverage for the at-ceiling case: unblock, fail once, still runnable.

## Related

- isaac-udlg — the delivery-skipped spam that was this bug's only visible symptom.
- isaac-s29x — self-healing recovery, split out of this bean.
- isaac-htix — `sessions list` does not show a blocked session, split out of this bean.
