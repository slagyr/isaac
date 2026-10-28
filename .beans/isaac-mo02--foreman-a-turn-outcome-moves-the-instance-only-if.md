---
# isaac-mo02
title: 'Foreman: a turn outcome moves the instance only if the current state is waiting on that turn'
status: completed
type: bug
priority: high
created_at: 2026-10-28T07:24:58Z
updated_at: 2026-10-28T07:53:48Z
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

## Landed on main (2026-10-28)

main-sha: isaac-agent eae0cdfa5adbc3eeb4e23a96df7ec030620e2661
main-sha: isaac-foreman fed1e6ef2faf1981cbe61bfe32f7892ee5e62500

Agent bb ci: 1962 specs and 940 feature examples, zero failures (one pending). Foreman bb ci: 108 specs and 56 feature examples, zero failures; focused acceptance: one example, six assertions. Bean gate PASS on the landed Foreman main commit.
