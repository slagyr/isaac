---
# isaac-mo02
title: 'Foreman: a turn outcome moves the instance only if the current state is waiting on that turn'
status: in-progress
type: bug
priority: high
created_at: 2026-10-28T07:24:58Z
updated_at: 2026-10-28T07:29:30Z
---

Found 2026-10-28 in isaac-q6fj's live acceptance run (test bean isaac-pqxn, machine isaac-bean-work on zanebot).

## What happened

1. The verify turn failed (provider overload), so the machine went verifying → verify-stalled and started the `:alert` turn (prowl, `plan-isaac-pqxn`).
2. Micah dispatched: verify-stalled → verifying, and a new verify turn started.
3. The alert turn, which was started on the earlier stall, ended quietly while the new verify turn was still running. Foreman emitted `:foreman/turn-ended` for the instance, and `verifying :foreman/turn-ended → verify-stalled` fired.
4. The verifier then finished correctly with `event: repair`. The instance was already in verify-stalled, so it was recorded `unhandled: repair` and dropped.

Every `:turn` action attaches the instance's turn observer, and `core/signal!` applies outcome events (`turn-ended`, `turn-failed`, `turn-died`, and reply events) from any of the instance's turns, whichever turn the current state is waiting on.

## Rule

A turn's outcome moves the instance only if the current state is waiting on that turn: the turn was submitted by the transition that entered the current state. An outcome from any other turn (one submitted on an earlier stay in this state, or in an earlier state) is recorded as unhandled and moves nothing. Reply events (`source :reply`) follow the same rule. CLI, HTTP and tool signals are not turn outcomes and are unaffected.

## Acceptance

`cd isaac-foreman && bb features features/foreman/turn_outcomes.feature:97`. The scenario holds a horn turn from an earlier stall in a closed pool, resumes, lets the horn end quietly (the instance stays tending, with the horn's end recorded unhandled), then lets the new lamp turn reply `event: lit` → lit.

Then the rest of `bb features` and `bb spec` stay green, and the isaac-foreman version is bumped for the deploy train.

feature-baseline: isaac-foreman 05a1431d8654e990b3b277b9615491092cc98c55
feature-blob: isaac-foreman features/foreman/turn_outcomes.feature 3064aef1ab8ece32bec428dc2b49e7669a7c7796 97

## Held (awaiting planner, 2026-10-28)

Escalated by **scrapper**@bean-isaac-mo02. Blocking: existing acceptance scenarios contradict the new rule or omit required turn identity. Resume only after the planner resolves these contracts and the upstream turn-drop context.

Implementation checkpoint: isaac-foreman `bean/isaac-mo02` at `90567a6` fences observer/reply outcomes by the matching pending turn's transition key and current state's entry transition; clears stale turns without moving state. The baselined scenario at `features/foreman/turn_outcomes.feature:97` and focused core specs pass; `bb bean-gate verify isaac-mo02 --dir isaac-foreman=../isaac-foreman-mo02` PASS. Manifest bumped to 0.6.1. Do not land while `bb ci` fails.

`bb ci` reports 107 specs, 0 failures; 56 features, **2 failures**:
- `features/foreman/events.feature:137` expects `foreman/turn-ended` and `foreman/turn-failed` from standalone `prompt --observer foreman:lighthouse-watch/beacon-*` to transition dark to unlit/storm-bound, despite those turns never being submitted by a transition entering dark. The new rule requires them to be unhandled. Planner must decide whether to change this older contract or explicitly exempt external turns (which conflicts with the stated rule).
- `features/foreman/turn_outcomes.feature:72` expects `turns drop #turn-id` to move tending to stalled. Upstream isaac-agent `turn/cli.clj` `run-drop` calls `observer/notify! :on-turn-died` with only `{:session-key ...}` and **no `:request-id`**, so Foreman cannot associate the dropped turn with the current state's submitted action. Upstream needs to supply the dropped turn id in that observer context; worker cannot fix this by altering Foreman's feature or treating idless outcomes as valid (that would let stale drops move any state). This is a cross-repo fix to plan/authorize.

No product branch landed; bean remains in-progress. On return, implement the agreed resolution, run `bb ci`, gate, then land. Existing feature files were not edited except removing `@wip` from the baselined scenario.

## Planner ruling (2026-10-28)

Both holds resolved; no contract lines change.

1. **The fence covers only turns Foreman submitted for this instance.** Read the Rule as: an outcome whose request id belongs to a turn Foreman submitted for this instance, but not on the transition that entered the current state, is recorded unhandled and moves nothing. A turn Foreman never submitted (attached from outside with `--observer foreman:<machine>/<instance>`) was pointed at the instance on purpose; its outcomes apply as before. So `features/foreman/events.feature:137` stays as written and must pass. The bug this bean fixes was Foreman's own `:alert` turn.
2. **Turn drops carry their id.** Authorized cross-repo change: isaac-agent `src/isaac/agent/turn/cli.clj` `run-drop` passes the dropped turn's id as `:request-id` in the `:on-turn-died` observer context (it has `id` in hand). Land it on isaac-agent main first, then bump isaac-foreman's isaac-agent pin to that landed sha. `features/foreman/turn_outcomes.feature:72` then passes unchanged. An outcome with no request id is treated like an external turn's (applied); with the drop fix, Foreman-submitted turns always carry one.

Resume from `90567a6`, run `bb ci` in both repos, gate, land.
