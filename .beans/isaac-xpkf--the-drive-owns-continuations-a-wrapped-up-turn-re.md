---
# isaac-xpkf
title: 'The drive owns continuations: a wrapped-up turn re-drives itself within a cycle.continuations budget'
status: completed
type: feature
priority: high
created_at: 2026-09-21T17:07:03Z
updated_at: 2026-09-21T17:59:25Z
blocking:
    - isaac-9azm
---

Hail used to own the continuation loop: when a turn hit its cycle limit,
hail's `wrap-up-delivery!` re-queued the delivery with `:continuation`
incremented (band `:continuations` budget, default 2) and dead-lettered with
attention when the budget ran out. That is turn orchestration wearing a
delivery costume (isaac-9azm). **Ruling (Micah, 2026-09-21): the drive owns
continuations; hail does not do them.**

## What the drive already owns

The in-turn half is already the drive's: on cycle exhaustion the loop asks
the comm (`comm/on-exhausted`), hail's comm answers `:wrap-up`, and
`apply-wrap-up-exhaustion` (`isaac-agent/src/isaac/drive/turn.clj`) runs the
pending tools, the wrap-up prompt, and persists the tool-less note
(isaac-ntt6, isaac-x0cw). The **only** thing hail added is "then start a
fresh turn on the same session, up to N times." That is this bean.

## Design

- **Budget key**: `:cycle {:continuations n}` beside `:limit`,
  `:checkpoint-every`, `:wrap-up-prompt`. Crew sets it; the charge's `:cycle`
  overlay (hail band, cron) overrides it exactly like `:limit` (isaac-tic5).
  Built-in default **2** (unchanged from hail's default). `isaac config schema
  crew.value.cycle` lists it. No zanebot band sets `:continuations` today, so
  nothing migrates.
- **Continuation = a queued turn.** After a turn ends `:cycle-limit` with
  `:exhaustion :wrapped-up`, and the continuation count is below the budget,
  the drive enqueues a fresh turn on the same session on the durable turn
  queue (`isaac.turn.queue`, the isaac-yxch resume path): a `:from-queue?`
  charge whose input is a short note telling the model to continue from its
  own wrap-up note. The queue record's origin carries the continuation count
  (and the original marker source, so staleness rules keep applying). The
  queue worker drives it. Restart-safe because the queue is durable.
- **Logs**: `:turn/continued {:session :continuation n :budget b}` on
  enqueue; `:turn/continuations-exhausted {:session :continuation n :budget
  b}` at ERROR when the count reaches the budget — plus attention
  (`attention/enqueue-attention!` via the existing outbox) and a comm
  bulletin `{:kind :turn/continuations-exhausted :text ...}` naming the
  session, so hail's comm (and any other) can surface it.
- **`:stop` never continues.** An attended comm that answers `:stop` gets
  today's summary reply and nothing is queued. The drive still never knows
  the origin — it only reads the policy answer and the cycle map.
- The drive stays generic: no hail, band, or bean knowledge in the turn path.

## Scenarios

`isaac-agent/features/turn/continuations.feature` (new, `@wip`) and the
schema row in `isaac-agent/features/config/cycle.feature`. The hail
scenarios this replaces (`isaac-hail/features/delivery.feature` "wraps up and
is re-queued as a continuation", "continuation budget exhausts", "default
continuation budget is 2") are deleted under isaac-9azm; their contract lives
here now.

## Acceptance

```
cd isaac-agent
bb features features/turn/continuations.feature
bb features features/config/cycle.feature
bb ci
```

- A wrapped-up turn is followed by exactly one queued continuation turn on
  the same session; the continuation's transcript starts from the wrap-up
  note.
- The budget is `:cycle {:continuations n}` (crew, overridden by the
  charge's cycle map), default 2, listed by `config schema crew.value.cycle`.
- Exhaustion logs `:turn/continuations-exhausted`, posts attention, and
  sends the comm a bulletin.
- A `:stop` policy queues nothing.
- Protocol/JVM: if the comm protocol changes, `bb jvm-spec` passes too.
- Downstream: do **not** repin isaac-hail in this bean; isaac-9azm bumps the
  hail pin and deletes hail's continuation branch.

feature-baseline: isaac-agent cabfdf29e81b87307a158b0fccd8056d0c03135d
feature-blob: isaac-agent features/turn/continuations.feature 95fb8662eecf9afa8c12810ccf5a0bbb403dbba6
feature-blob: isaac-agent features/config/cycle.feature 458700b7d5fc5086ae4c67692c2ab7fb4bf3f72a 26



Dispatched: hail 24a7dc2a 2026-09-21T17:15:30Z (band isaac-work)

## Landed on main (2026-09-21)

main-sha: isaac-agent 2481eb95d7cd9035a4b4cb173668b3b159b0df6f

Squash of `bean/isaac-xpkf` @ `90a855a` (base `359bcea`). No downstream repin —
isaac-9azm bumps the hail pin.

### What was built

**The budget** — `:cycle {:continuations n}` layers exactly like `:limit`:
built-in default **2** (`isaac.drive.turn/default-continuations`), then
`:defaults :cycle`, then crew, then the charge's `:cycle` overlay (hail band,
cron). `resolve-cycle` fills and coerces it, so one call answers both knobs.
Declared in `resources/isaac-manifest.edn` under `crew.value.cycle` and
`defaults.cycle`, so `isaac config schema crew.value.cycle` lists it.

**The re-drive** — a new Continuations region in `src/isaac/drive/turn.clj`:

- `continuation-count` reads the count off the charge's `:origin`; the drive
  keeps no state between turns.
- `continuation-plan` is the whole decision: only a `:cycle-limit` /
  `:wrapped-up` turn continues (`:stop` answers `:stopped`, so an attended comm
  never continues), `:continue` while the count is under budget, `:exhausted`
  once it reaches it.
- `enqueue-continuation!` persists the note as a user message (the wrap-up note
  is the transcript's last assistant message, isaac-x0cw, so the model reads its
  own done/next straight above it) and parks a fresh turn on the durable turn
  queue — the same waiting room a resumed turn uses (isaac-yxch). The record's
  origin carries `:continuation n` plus the original source. Logs
  `:turn/continued {:session :continuation :budget}`.
- `continuations-exhausted!` logs `:turn/continuations-exhausted` at **error**,
  posts attention (`attention/maybe-notify-continuations-exhausted!`, new), and
  sends the comm a `{:kind :turn/continuations-exhausted :text …}` bulletin
  naming the session.
- `maybe-continue!` runs once per finished turn from `run-turn!`'s `finish!`,
  wrapped so a queue or comm failure can never fail the turn it follows.

The drive stays generic: no hail, band or bean knowledge anywhere in the path —
it reads the policy answer, the cycle map and its own origin.

**The comm across the queue** — the bulletin has to reach the comm that asked
for the wrap-up, and the continuation has to be able to wrap up again, so the
live channel travels with the parked turn: `isaac.turn.queue` keeps it in a
process-local side table keyed by record id (`live-comm`, `forget-live-comm!`),
never in the EDN, and `turn.worker/wake-charge` attaches it when waking. After a
restart the attachment is gone and the continuation wakes comm-less — exactly
like a resumed turn.

### Tests

- `features/turn/continuations.feature` — 4 scenarios, `@wip` removed, green.
- `features/config/cycle.feature` — schema row, `@wip` removed, green.
- `spec/isaac/drive/turn_spec.clj` — 4 new examples (budget layering and
  coercion, origin count, the plan table incl. `:stop` and a zero budget, the
  note's wording). Written first; each was red before the drive change.
- Step fix (not a feature edit): `comm_steps/record-memory-turn!` stored a
  *snapshot* of the memory comm's events, so nothing a later queue-driven turn
  sent was visible to `the memory comm has events matching:`. It now stores the
  atom, matching what `session_steps` already does.

`bb ci` exit 0 on the rebased branch: **1691 specs / 0 failures**, **844
features / 0 failures** (1 pending `@wip` belongs to another bean). Gate PASS on
the branch (`90a855a`) and again on the squash (`2481eb9`).

### Environment note (not this bean)

Full-suite runs flaked twice with 2 and then 75 failures, all in
`features/config/schema_cli_options.feature` and all shaped like
`config-schema collision at :comms … :isaac.agent/comm vs :isaac.server/comm`.
Cause: that feature's `Given an empty Isaac root at "/tmp/isaac"` is a
**hard-coded absolute path shared by every checkout on this machine**, and 6–8
other `bb features` processes were running concurrently in sibling worktrees.
Run in isolation the file is 7/0 three times in a row, and two clean full runs
(the ones recorded above) are 0 failures. Filed as a follow-up bean.
