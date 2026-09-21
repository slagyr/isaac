---
# isaac-udlg
title: hail delivery-skipped logs at warn on every poll (357 in one log)
status: completed
type: bug
priority: normal
created_at: 2026-09-21T21:09:23Z
updated_at: 2026-09-21T22:09:46Z
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

## Landed on main (2026-09-21)

main-sha: isaac-hail 7994a122a7cf0528e9cf85daa1a02649f3ac145a

Shipped in two passes, because the first was the wrong fix:

1. `f6a18a3` — expected skip reasons (`:session-in-flight`, `:crew-at-capacity`)
   moved from `:warn` to `:debug`; `:session-missing` still warns. This changed
   severity but not volume, and the noise remained.
2. `7994a12` — the actual fix. `log-skipped!` remembers the last reason logged
   per delivery and speaks only when it changes; launching a delivery forgets
   it so a later wait is announced afresh. `defonce` so a reload does not
   re-announce every queued delivery.

Verified on zanebot after deploy: **one** `delivery-skipped` line for a waiting
hail across a 34-second window, against hundreds before.

### Contract amended (authorized by Micah)

isaac-at5m's scenario "logs why it was skipped on every tick" is now "says why
it is waiting once, not once per tick", asserted with `the log has exactly 1
entries matching:` across two ticks. That is a **stronger** pin than the
original, which only checked presence and would have passed at any volume.
at5m's contract — a bound delivery never sits unclaimed silently, with its
reason named — is intact.

### Deploy note worth keeping

Bumping a module pin and restarting is **not** sufficient: the coord is fetched
lazily by a later CLI call, so the daemon boots on the old classpath. The first
restart of this fix ran old code for that reason. The order is: bump the pin →
run a CLI command to trigger the fetch → confirm the sha exists under
`~/.gitlibs/libs/<module-id>/<module-id>/<sha>` → then restart.

(`~/.gitlibs/libs/io.github.slagyr/isaac-hail/...` is a different, transitive
copy and is not what the daemon loads. Grepping there gives a false negative.)

`bb ci`: 173 specs / 0, 136 features / 0.
