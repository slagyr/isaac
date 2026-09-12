---
# isaac-lrue
title: 'Restart loses in-flight hail turns: resume leaves the legacy turn marker, hail''s stray sweep deletes the just-requeued deliveries (3 beans lost 2026-09-11)'
status: in-progress
type: bug
priority: critical
tags:
    - agent
    - hail
    - resume
created_at: 2026-09-11T04:10:14Z
updated_at: 2026-09-12T14:06:39Z
---

Repos: **isaac-agent** (`bridge/resume.clj`, `session/store/impl_common.clj`
turn-marker paths, `session/migrate.clj`) and **isaac-hail**
(`delivery_worker.clj` stray sweep).

## What happened (zanebot, 2026-09-11 04:00Z, train agent 0.1.60 → 0.1.63)

Three hail turns were in flight when the server was gracefully restarted for
the train. The 0.1.60 suspend wrote their turn markers at the **legacy** path
`sessions/<id>/turn.edn`. The 0.1.63 boot (b6w0's per-crew layout
`sessions/<crew>/<id>/`) ran the resume scan, read the legacy markers,
requeued the three deliveries (`:resume/scan-complete :requeued 3`), then
called `clear-turn-marker!` — which cleared the **new-path** marker that did
not exist and left the legacy file in place. 1.3 s later the hail delivery
worker's stray sweep (isaac-7li9 / 3tyl: "marker references delivery, session
not in flight ⇒ claim-crash stray") found the still-present legacy markers,
deleted the three freshly requeued deliveries and logged
`:hail/stale-delivery-removed` (id 70d4d9c5 isaac-tic5, a403c825 isaac-udnm,
7c6354f1 tono-bzg0). No turn ever bound. A second restart at 04:05 (hail
rolled back to 0.1.16 on the wrong theory) did exactly the same thing — the
event had never appeared in any log before 2026-09-11.

Recovered by hand: session-direct re-hails (3f023087, ebbbff24, f9d312b5) and
the three legacy markers moved to `hail/stale-markers-20260911/`.

## Defects

1. **Resume clears the wrong marker path.** After requeueing from a legacy
   marker, resume must clear the marker it *read* (legacy or current), not
   only the current path. `session/migrate.clj` already knows both paths.
2. **The stray sweep can eat a just-requeued delivery.** "Marker present,
   session not in flight" is also the honest state of every delivery the
   resume scan wrote a second ago. The sweep needs either a grace period
   after boot / after requeue, or a `:requeued-at` stamp on the delivery that
   exempts it until it is claimed once. A hail must never be deleted by a
   race with its own resume ([[hails-never-die]]).
3. **Silent loss.** `:hail/stale-delivery-removed` is a `:warn` and nothing
   posts attention; three beans vanished from the queue without a message.
   Route it through the dead-letter attention path.

## Acceptance (scenarios to plant)

isaac-agent `features/bridge/suspend.feature` or `session/resume_repair.feature`:
- a suspended hail marker at the legacy path is requeued AND the legacy file
  is gone afterwards (no marker left at either path).
isaac-hail `features/turn-resume.feature`:
- a delivery requeued by resume with its marker still on disk is NOT swept
  on the next tick; it binds (grace or stamp per decision).
- a genuine claim-crash stray (marker, no requeue stamp, session idle past the
  grace) is still removed, and the removal posts attention.

```
cd isaac-agent && bb features features/bridge/suspend.feature features/session/resume_repair.feature && bb spec && bb ci
cd isaac-hail && bb features features/turn-resume.feature features/delivery.feature && bb spec && bb ci
```

Until this lands: **after every zanebot restart, grep server.log for
`stale-delivery-removed` and re-hail session-direct anything it names.**

## Work checkpoint (2026-09-12, scrapper@isaac-work-2)

Done: Agent branch `bean/isaac-lrue` is clean and pushed at `3a1cbff`: marker clearing removes current, flat, and legacy paths; resume requeues carry deterministic `:resume/requeued-at`; required focused features are green (8 examples, 25 assertions) and full specs are green (1597 examples, 3292 assertions). Hail was rebased onto `origin/main@e344919`, is clean and pushed at `0bca2f6`: provenance-based 60-second sweep grace, genuine-stray removal attention, and inherited restart specs are implemented; required focused features are green (34 examples, 122 assertions), turn-marker claim is green (4 examples, 24 assertions), and full specs are green (160 examples, 371 assertions).

Next/red: broad `bb ci` is still red only in known upstream suites. Agent main CI at `374ea9e` has the same 19 acceptance failures; its repair exists on `origin/bean/isaac-kwhb@49e8190` but is not landed, and full-suite execution hits the 180-second cap. Hail main CI cannot resolve its Agent pin `86a9a92`; this bean pins its public Agent SHA and advances to feature execution, where 13 pre-existing band/config failures remain (focused reruns now reproduce 6; prompt templating passes in isolation). Do not absorb unrelated fixes into this critical bean. Resume at `isaac-agent-lrue/spec/isaac/config/agent_steps.clj:1` only if upstream `isaac-kwhb` lands; otherwise rerun Hail `bb ci` from `isaac-hail-lrue/bb.edn:35` after upstream main repairs, then record exact gate evidence.
