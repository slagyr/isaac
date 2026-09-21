---
# isaac-xpkf
title: 'The drive owns continuations: a wrapped-up turn re-drives itself within a cycle.continuations budget'
status: in-progress
type: feature
priority: high
created_at: 2026-09-21T17:07:03Z
updated_at: 2026-09-21T17:16:04Z
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
