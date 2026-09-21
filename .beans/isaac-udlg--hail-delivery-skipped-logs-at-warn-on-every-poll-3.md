---
# isaac-udlg
title: hail delivery-skipped logs at warn on every poll (357 in one log)
status: todo
type: bug
priority: normal
created_at: 2026-09-21T21:09:23Z
updated_at: 2026-09-21T21:09:23Z
---

Repo: **isaac-hail** (`src/isaac/hail/delivery_worker.clj`).

## What happens

`:hail/delivery-skipped, :reason :session-in-flight` is logged at **:warn** on
every poll while the target session is busy. On zanebot today: **357 occurrences
in one log file**.

A busy session is the normal, healthy case — a worker is mid-turn and the queued
hail waits for it. It is not a warning, and it is certainly not 357 warnings.

## Why it matters

The volume buries real warnings. Today it sat directly on top of a genuine
`claude binary failed` (which occurred exactly **once**) and the config
unknown-key lines from isaac-nq4c, making the operator read the log as "lots of
errors" when the pipeline was in fact healthy. Micah: "Why is that delivery skip
message being printed so often? That's spammy."

Same failure mode as isaac-6eu6: output that fires on every tick trains the
reader to ignore the channel it shares with real problems.

## Change

- Log the skip at **:debug**, not :warn — a busy session is expected.
- Or, if an operator-visible signal is wanted, log **once per state transition**
  (first skip for a given hail/session pair), not once per poll, with the
  duration on resolution.
- Same treatment for any sibling per-poll delivery log.

## Acceptance

- A hail waiting on a busy session produces at most one :warn-or-above line,
  regardless of how long it waits.
- The delivered/failed outcome is still logged at :info or above.
- A log file covering a normal working day contains no repeated
  `delivery-skipped` warn lines.
