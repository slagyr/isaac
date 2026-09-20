---
# isaac-bsqm
title: 'Hail delivery stalled fleet-wide after the 2026-09-19 upgrade: bound deliveries are never attempted'
status: completed
type: bug
priority: critical
tags:
    - hail
    - ops
created_at: 2026-09-20T00:24:25Z
updated_at: 2026-09-20T01:19:03Z
---

Repo: **isaac-hail** (possibly isaac-foundation's scheduler). Found 2026-09-19 ~23:00Z on zanebot, right after the fleet upgrade (foundation 1a47992 → ba7fa5b, hail 4dc44ef → d0f8932, agent fc30085 → fd89226, plus 7 other modules).

## Symptom

**No hail has been delivered on zanebot since 2026-09-19T23:07Z.** Four deliveries sit in `~/.isaac/hail/deliveries/` with `:attempts 0`, including the isaac-6krg verify hail and two planner probes sent after the upgrade. Nothing in the worker pipeline runs: no beans dispatch, no verify, no continuations.

## What is NOT the cause (checked)

| checked | result |
|---|---|
| auth / rotten token | not involved — `POST /hail/send` returns 201 with `:principal "admin"`; routing runs after auth |
| routing | works — probe 461b75b2 shows `:bound-session :isaac-plan :crew :prowl`. (`:candidates 0` in `hail/routed` is normal for reach `:one`: it binds instead of pooling) |
| session registry | `isaac sessions list` shows every session with correct crew and tags |
| sessions stuck in-flight | `isaac sessions list --in-flight` → none; `isaac turns list` → none |
| held turns | none |
| component start | `:component/started :component "hail-runtime" :module "isaac.hail"` at every boot |
| scheduler health | shared scheduler runs — `episodes/tick` fires on it continuously |
| scheduler errors | no `:scheduler/handler-error` events at all |
| config validity | was invalid (retired `:server` keys) until 23:45Z; cleaning it changed nothing |
| restart | three restarts; queue never drains |

## Where the trail ends

`isaac.hail.component/HailRuntime.start` calls `router/start!` and `delivery-worker/start!`, each scheduling a 1s interval task (`:hail/route`, `:hail/deliver`) on the shared scheduler. Routing demonstrably happens, so `:hail/route` fires. `:hail/deliver` produces **zero** log lines after boot — no bind, no claim, no defer, no error — while `due?` is true for every queued delivery.

## Next steps

1. Prove whether `:hail/deliver` is registered and firing at runtime. The scheduler has no inspection surface — consider `isaac scheduler list` (id, next-fire-at, consecutive-errors, disabled?) as the general fix; it is the missing observability that made this a multi-hour hunt.
2. `scheduler/schedule!` throws on a duplicate `:id`. Check whether anything registers `:hail/deliver` twice (config reload, double component start) and whether that throw is swallowed.
3. Instrument the tick's silent skip paths: `referenced-delivery-ids` markers, `resume-grace?`, and `session-available?` (`in-flight?` plus `crew-available?` against `:max-in-flight`) all `nil` out without logging.
4. Bisect: pin hail back to 4dc44ef on zanebot with the new foundation. If delivery resumes, it is hail d0f8932; if not, foundation ba7fa5b.

## Acceptance

Scenarios (worker writes, isaac-hail features): a bound delivery whose session is idle is claimed on the next tick; a delivery skipped for each silent reason logs that reason once; the worker recovers deliveries that were bound before a restart. Plus whatever the root cause demands.

Operationally: on zanebot the four queued deliveries drain and `hail/turn-ended` appears again.

## Round 2 (planner, 2026-09-20 00:30Z)

**Blocked turns / stuck sessions ruled out.** `store/turn-markers` has no marker for any queued delivery, `isaac turns list` is empty, and the on-disk session entries (`sessions/<crew>/<id>/session.edn`) carry no in-flight or held state — in-flight lives in a server-memory atom that three restarts cleared. The grok 403 wall did cause the original 23:07Z deferral, but the queue has been frozen since the upgrade restart, not since grok.

**The router tick runs; the delivery tick does not.** `queue/send!` only writes to `hail/pending` — it does not route. Probe 461b75b2 was routed and bound to `:isaac-plan`/`:prowl` after the upgrade, so `:hail/route` fires on the shared scheduler. Meanwhile `15e636f1` sits with `:next-attempt-at 2026-09-19T23:11:51Z`, 79 minutes overdue, `:attempts 0`, bound to `:isaac-verify`.

**Every non-silent path is absent.** d0f8932's tick logs `:hail/delivery-skipped` with a reason (`:session-in-flight`, `:crew-at-capacity`, `:session-missing`) whenever it declines a bound delivery, and `launch-delivery!` logs on success. Neither appears. No `:scheduler/handler-error` in any log file, so the handler is not throwing. `scheduler/tick!` iterates every task each cycle, so a 1s task cannot starve another 1s task.

**Two candidates remain**

1. `:hail/deliver` was never registered even though `hail-runtime` logged `component/started` (`HailRuntime.start` calls `router/start!` then `delivery-worker/start!`).
2. The tick runs but `list-deliveries root` sees nothing — the one path in `tick!` that produces neither a launch, a skip log, nor an error. That implies `runtime-root` resolves differently in the delivery thread than in the router thread; the two namespaces carry separate copies of that helper.

**Next: bisect on zanebot.** Pin `isaac.hail` back to 4dc44ef against the new foundation and restart. Queue drains ⇒ hail d0f8932; queue stays ⇒ foundation ba7fa5b. Either way, `isaac scheduler list` (id, next-fire-at, consecutive-errors, disabled?) would have answered this in one command and should land with the fix.

## Round 3 (planner, 2026-09-20 00:45Z) — the version pairing is exonerated

A repro spec wires the **real** `:hail-runtime` component to a **real** scheduler (the shipped `component_spec` stubs `router/start!` and `delivery-worker/start!`, so nothing ever exercised scheduling):

- hail d0f8932 with its own pinned foundation df64bf1 → both `:hail/route` and `:hail/deliver` register, and the delivery tick fires. Green.
- hail d0f8932 with **zanebot's** foundation ba7fa5b → same. Green.

So the deployed pair wires correctly in a clean process, and **a rollback to 4dc44ef probably will not fix zanebot**. The failure is state, not code pairing. (That repro belongs in the repo: `component_spec` asserting a real `scheduler/list-tasks` would have caught a registration regression.)

Live inspection of zanebot, all read-only:

| probe | result |
|---|---|
| thread dump (`jcmd Thread.print`, 74 threads) | no thread inside `isaac.hail.*`, `delivery_worker` or `drive.turn`; all 8 `isaac-scheduler-*` threads parked idle |
| child processes | no `claude` or model subprocess hung |
| turn markers | none live (one `turn.edn.cancelled-*` from 09-09) |
| scheduler events in any log | none at all since boot — no `handler-error`, no `disabled` |

**Leading hypothesis: stale `:active-run` on `:hail/deliver`.** `compute-tick-transition` will not begin a run while `:active-run` is set; if a run's finish transition never lands, the task goes silent forever with no log line and no thread. The first tick after boot had an overdue delivery to launch, which fits the timing exactly. The scheduler has no way to show this, which is the real gap.

**Queue hygiene found on the way:** `ed0d19f9` was a `ci-failure` delivery from **2026-09-04**, attempts 4, bound to session `:cheery-rowan`, sitting in `deliveries/` for 15 days. Dropped it and `15e636f1` (isaac-6krg, since completed) to test whether a poison record at the head of the scan blocks the tick.

## Round 4 (planner, 2026-09-20 00:50Z) — restart with a clean queue changes nothing

Dropped the two obsolete deliveries, restarted the service (boot 00:41:45Z, all 8 components started including `hail-runtime`), then sent a fresh band hail `ee647adf`.

| | |
|---|---|
| router half | **works** — `ee647adf` went pending → `hail/routed` → `deliveries/` within 8s |
| delivery half | **silent** — zero `hail/*` lines since boot; three deliveries queued and due |
| warnings/errors since boot | none (an earlier "nothing since boot" claim used an off-by-one `awk` timestamp filter and was worthless; rechecked with a string match) |
| component start | `component/started hail-runtime` present in this boot; `components/start-all!` rethrows on failure, so `delivery-worker/start!` did run and `schedule!` did not throw |
| scheduler errors/timeouts | none — `:scheduler/handler-error` and `:scheduler/timeout` both log unconditionally and neither appears |

So on zanebot `:hail/route` and `:hail/deliver` are registered by the same component start, one fires every second and the other has never fired. Restarting does not clear it, a clean queue does not clear it, and the same code fires correctly in the repro.

**Repro spec pushed:** `bean/isaac-bsqm` in isaac-hail (7380927) — real component, real scheduler, asserts `scheduler/list-tasks` contains both ids and the tick runs.

**Two ways forward**

1. Blunt: repin `isaac.hail` to 4dc44ef on zanebot and restart. The repro argues this will not help, but it is one command and the host is idle anyway.
2. Fix forward: ship scheduler observability (`isaac scheduler list` printing id, next-fire-at, active-run, consecutive-errors, disabled?) and a one-line log when a task registers. That answers "is it registered and when did it last fire" in one command instead of an evening of inference — and it is the gap this whole hunt exposed.

## Root cause and fix (2026-09-20 01:20Z)

`isaac.hail.delivery-worker/tick!` ends in a `(->> … (keep …))` whose terminal `vec` was dropped in d0f8932. The result is a **lazy sequence that nobody realizes**: the scheduler's handler is `(fn [_] (tick! {}))` and throws the return value away, so the worker walked the queue and launched nothing — no bind, no skip log, no error, forever.

Every existing example in `delivery_worker_spec` calls `(first (sut/tick! …))`, and `first` forces the seq. That is why 32 green examples coexisted with a worker that delivered nothing in production.

Proved by running one tick in a separate JVM on zanebot with the real root, real config and `launch-delivery!` stubbed: `TICK OK`, zero launches, while the same records report `runnable true` when the helpers are called directly.

**Fix:** restore the terminal `vec`, plus an example that ignores the return value (`isaac-hail` 21cef0e, on main). It fails without the fix and passes with it. `bb spec` 172/0; `bb features` 163 with the 6 pre-existing deferral failures from isaac-ox53, unchanged.

**Deployed:** registry bumped, `isaac modules upgrade isaac.hail` on zanebot (d0f8932 → 21cef0e), service restarted 01:15Z. All four stuck deliveries drained immediately: `hail/bound` for each, the isaac-9mkp work hail is running on Claude Opus 5 (a live `claude` process), and the two planner probes bound to isaac-plan and suspended on grok's 403 (prowl is still `:grok-4-6` by design).

Also fixed in passing: `hail/delivery-skipped` now actually reaches the log, so a bound delivery whose session is missing says so.

main-sha: isaac-hail 21cef0e57a5ac11f447322475fb588b89afff9b1
