---
# isaac-f9fh
title: Shared scheduler for cron, delivery worker, and module-driven periodic tasks
status: draft
type: feature
priority: deferred
created_at: 2026-05-20T04:57:24Z
updated_at: 2026-05-20T04:57:24Z
---

## Gap

Periodic/delayed work in Isaac is currently a copy-pasted pattern. `isaac.cron.scheduler`
and `isaac.comm.delivery.worker` each own their own thread:

```clojure
(let [running? (atom true)
      runner   (future
                 (while @running?
                   (tick! ...)
                   (Thread/sleep tick-ms)))]
  ...)
```

iMessage will copy it again (incoming polling at 1–2 Hz, outbound delivery).
Future comm modules and tools will too. There is no shared abstraction for
"wake me on a schedule and call this fn."

## Impact

- Thread budget grows linearly with modules. Each long-lived module loop is
  its own platform thread.
- No central place to observe, cancel, or introspect scheduled work.
- No one-shot delayed task primitive — delivery worker's exponential backoff
  is implemented as state-on-disk + a poll loop because nothing in Isaac can
  say "fire this once in 30 seconds."
- Each module reimplements stop semantics, error handling, missed-fire
  policy.

## Proposed change

Introduce `isaac.scheduler` — a single component that owns timing and lets
modules register tasks as `(trigger, handler)` pairs.

```clojure
(scheduler/schedule!
  {:id       :delivery/tick
   :trigger  {:kind :interval :ms 10000}
   :handler  (fn [_ctx] (delivery/tick!))
   :coalesce :skip})

(scheduler/schedule!
  {:id       :cron/nightly-cleanup
   :trigger  {:kind :cron :expr "0 3 * * *" :zone "America/Chicago"}
   :handler  (fn [ctx] ...)})

(scheduler/schedule-once!
  {:id      :delivery/retry-7f3a
   :trigger {:kind :delay :ms 30000}
   :handler (fn [_] (delivery/retry "7f3a"))})

(scheduler/cancel! :delivery/tick)
(scheduler/list)
```

### Trigger taxonomy (extensible)

Triggers are plain data. Dispatch via multimethod on `:kind`:

| `:kind`     | Shape                       | Use case                          |
|-------------|-----------------------------|------------------------------------|
| `:interval` | `{:ms N}`                   | High-freq polling, periodic tick   |
| `:cron`     | `{:expr "..." :zone "..."}` | Calendar schedules                 |
| `:delay`    | `{:ms N}`                   | One-shot delayed (retry, "in 5m")  |
| `:at`       | `{:instant ...}`            | One-shot absolute time             |

Adding a new trigger kind is one defmethod implementing `(next-fire-at trigger now state)`.

### Threading model (Java 21)

Two layers:

1. **Timer** — a single `ScheduledExecutorService` (one thread) computes "what
   fires next." No work runs here.
2. **Work executor** — `Executors/newThreadPerTaskExecutor(Thread.ofVirtual().factory())`.
   When a trigger fires, the timer hands the task off here. Virtual threads
   make per-task threads ~free, so iMessage polling at 2 Hz, delivery ticks
   at 10 s, and dozens of cron jobs all coexist under a tiny resource budget.

Critical property: a slow handler must not block the timer. iMessage polling
stalling cannot delay the cron evaluator.

### Per-task policies

- `:coalesce` — `:skip` | `:queue` | `:run` (default `:skip`).
- `:on-error` — `:log` (default) | `:retry-with-backoff` | `:disable-after-N`.
- `:timeout-ms` — kill handlers that hang; logs `:scheduler/timeout`.
- Clock injection for testing — `(scheduler/with-clock c ...)`.

### Persistence — deferred

`:persist?` (durable across restart) is **out of scope for v1**. Cron jobs
don't need it (config defines them); delivery worker can keep its on-disk
queue. Add later if a use case (long one-shot delays surviving restart)
demands it.

## Surface

### New code

- `isaac.scheduler` — core: registry, schedule!/schedule-once!/cancel!/list,
  two-layer executor, lifecycle integration with `isaac.system`.
- `isaac.scheduler.trigger.interval`
- `isaac.scheduler.trigger.cron` — wraps the expression evaluator extracted
  from `isaac.cron.cron`. Existing parser is fine; it already computes
  `next-fire-at` / `previous-fire-at`.
- `isaac.scheduler.trigger.delay`
- `isaac.scheduler.trigger.at`

### Migrations (each independently shippable)

1. Build `isaac.scheduler` with `:interval` + `:delay` triggers and the
   two-layer executor.
2. Migrate `isaac.comm.delivery.worker` — `start!` becomes
   `(scheduler/schedule! :delivery/tick ...)`. `tick!` stays. Delete the
   `future`/`while` loop.
3. Add `:cron` trigger. Extract `isaac.cron.cron` evaluator into
   `isaac.scheduler.trigger.cron`.
4. Migrate `isaac.cron.scheduler` to register one task per configured cron
   job. `isaac.cron.state` becomes scheduler state.
5. Add `:at` trigger.

After step 1, scheduler is usable with one customer. Each subsequent step is
independent.

### Naming

`isaac.scheduler` clashes with existing `isaac.cron.scheduler`. Rename the
cron one to `isaac.cron.jobs` since it'll become a thin registration layer
over the scheduler.

## Open questions

1. **Trigger as data vs constructor function?** Data is easier to log,
   inspect, and (eventually) persist. Constructor fns are easier for user
   extension. Lean: start with data.
2. **iMessage at 500 ms — is interval+coalesce sufficient,** or does the
   incoming-poll loop need a different primitive (e.g., long-poll with a
   blocking handler)?
3. **Cross-module discoverability** — should scheduled tasks be declared in
   module manifests (like other module-contributed kinds) or registered
   programmatically at module startup? Manifests give a static, schema-able
   view but are less flexible.
4. **Missed-schedule semantics for `:cron`** — `isaac.cron.scheduler`
   currently logs `:cron/missed-schedule` when a job is late by more than
   `tick-ms`. Carry that policy forward, or unify with `:coalesce`?

## Origin

Surfaced while planning iMessage outbound and incoming polling
(`isaac-imessage`). The pattern duplication was already obvious between
cron and delivery worker; adding iMessage made it the right time to
generalize.
