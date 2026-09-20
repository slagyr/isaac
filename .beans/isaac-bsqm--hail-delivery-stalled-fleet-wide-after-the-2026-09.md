---
# isaac-bsqm
title: 'Hail delivery stalled fleet-wide after the 2026-09-19 upgrade: bound deliveries are never attempted'
status: todo
type: bug
priority: critical
tags:
    - hail
    - ops
created_at: 2026-09-20T00:24:25Z
updated_at: 2026-09-20T00:24:25Z
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
