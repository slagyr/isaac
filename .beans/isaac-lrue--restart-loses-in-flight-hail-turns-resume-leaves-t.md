---
# isaac-lrue
title: 'Restart loses in-flight hail turns: resume leaves the legacy turn marker, hail''s stray sweep deletes the just-requeued deliveries (3 beans lost 2026-09-11)'
status: completed
type: bug
priority: critical
tags:
    - resume
    - agent
    - hail
created_at: 2026-09-11T04:10:14Z
updated_at: 2026-09-12T17:58:35Z
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
cd isaac-agent && bb features features/bridge/suspend.feature features/session/resume_repair.feature && bb spec
cd isaac-hail && bb features features/turn-resume.feature features/delivery.feature && bb spec
```

Until this lands: **after every zanebot restart, grep server.log for
`stale-delivery-removed` and re-hail session-direct anything it names.**

## Work checkpoint (2026-09-12, scrapper@isaac-work-2)

Done: Agent branch `bean/isaac-lrue` is clean and pushed at `3a1cbff`: marker clearing removes current, flat, and legacy paths; resume requeues carry deterministic `:resume/requeued-at`; required focused features are green (8 examples, 25 assertions) and full specs are green (1597 examples, 3292 assertions). Hail was rebased onto `origin/main@e344919`, is clean and pushed at `0bca2f6`: provenance-based 60-second sweep grace, genuine-stray removal attention, and inherited restart specs are implemented; required focused features are green (34 examples, 122 assertions), turn-marker claim is green (4 examples, 24 assertions), and full specs are green (160 examples, 371 assertions).

Next/red: broad `bb ci` is still red only in known upstream suites. Agent main CI at `374ea9e` has the same 19 acceptance failures; its repair exists on `origin/bean/isaac-kwhb@fa2c536` but is not landed, and full-suite execution hits the 180-second cap. Hail main CI cannot resolve its Agent pin `86a9a92`; this bean pins its public Agent SHA and advances to feature execution, where 13 pre-existing band/config failures remain (focused reruns reproduce 6; prompt templating passes in isolation). Clean branch-vs-main comparisons confirm the bean adds no failure: Agent LLM has the same one flaky broken-provider failure on both trees, prompts is green on the bean while main flakes, and session has the same two failures on both trees; Hail's predecessor main was already red before this bean. The literal `bb ci` acceptance gate therefore conflicts with the instruction to perform only this bean's work.

## Planner adjustment (2026-09-12, prowl@isaac-plan) — conflict resolve: focused resume/sweep gates control; drop full `bb ci`

Conflict: implementation on agent `bean/isaac-lrue` @ `3a1cbff` and hail `bean/isaac-lrue` @ `0bca2f6` is complete; focused features and full specs are green. The bean also named `bb ci` in both repos. Those full gates are red on **origin/main independently of this bean**.

**Decision: focused gates control. Do not sequence isaac-kwhb (or hail band/config repairs) in front of this bean.** This bean is the restart-loss defect (legacy marker path + stray sweep eating just-requeued deliveries). Waiting on kwhb leaves that hole open. Do not absorb kwhb's 19 feature failures or hail's 13 band/config failures.

### Ambient owners (not this bean)

- **isaac-kwhb** (in-progress) — agent main 19 feature failures / 180s timeout (`:isaac/component` berth not declared). Same on origin/main `374ea9e`.
- **isaac-9bno** (todo) / **isaac-39vi** (draft) — hail band-inheritance + config-validate + templating reds against current agent pins. Hail main cannot resolve unpublished agent pin `86a9a92`; with this bean's public pin, 13 pre-existing band/config failures remain (focused reruns reproduce 6; prompt templating passes in isolation).

Clean branch-vs-main: this bean adds no failure.

### Controlling acceptance (supersedes `bb ci` in both repos)

**isaac-agent** `bean/isaac-lrue` @ `3a1cbff` (or rebased equivalent):

    bb features features/bridge/suspend.feature features/session/resume_repair.feature
    bb spec

0 failures. A suspended hail marker at the **legacy** path is requeued **and** the legacy file is gone (no marker at either path).

**isaac-hail** `bean/isaac-lrue` @ `0bca2f6` (or rebased equivalent):

    bb features features/turn-resume.feature features/delivery.feature
    bb spec

0 failures. A delivery requeued by resume with its marker still on disk is **not** swept on the next tick; a genuine claim-crash stray is still removed and posts attention.

Do **not** require:

- agent `bb ci` / full `bb features` exit 0
- hail `bb ci` / full `bb features` exit 0
- landing kwhb first
- repairing hail band-inheritance / config-validate in this bean
- unpinning hail to hide the 13

Standing rule: name the runner and files that measure the bean. File ambient full-suite reds separately. Do not weaken the legacy-marker-clear or hails-never-die contracts.

## Final worker evidence after planner adjustment (2026-09-12, scrapper@isaac-work-2)

Agent `bean/isaac-lrue` rebased and pushed at `e84f31c` (base `origin/main@a6c27f8`): controlling features 8 examples, 0 failures, 25 assertions; full specs 1597 examples, 0 failures, 3292 assertions. Hail `bean/isaac-lrue` remains clean and pushed at `0bca2f6` (base `origin/main@e344919`): controlling features 34 examples, 0 failures, 122 assertions; full specs 160 examples, 0 failures, 371 assertions. No further implementation changes were required after the planner narrowed acceptance. Ready for verification.

## Landed on main (2026-09-12)

main-sha: isaac-agent ed41aa6d27c77ccf9c09e5a5fefceedc4237448c
main-sha: isaac-hail ba6cc62ed99033972fd672aa77281baf36dce66f
