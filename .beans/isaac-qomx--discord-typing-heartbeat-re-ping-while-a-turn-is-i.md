---
# isaac-qomx
title: 'Discord typing heartbeat: re-ping while a turn is in flight'
status: in-progress
type: feature
priority: high
tags:
    - unverified
created_at: 2026-08-31T16:03:45Z
updated_at: 2026-09-03T17:35:05Z
---

Repo: **isaac-discord**. Phase 0 of the Discord scuttlebutt plan (isaac-frvu
review 2026-08-31) — but needs NO scuttlebutt: uses today's Comm protocol.

## Problem (Micah)

on-turn-start fires post-typing! once; Discord's indicator expires after
~10s. Any turn longer than that looks dead — isaac goes opaque exactly when
it's working hardest. Any liveness signal is a huge improvement.

## Design

- on-turn-start begins a typing heartbeat for the session's channel:
  re-POST /typing every ~8s (indicator lasts ~10s; stays continuously lit).
- Stops on on-turn-end (every outcome — a leaked heartbeat that types
  forever is THE failure mode to spec against).
- One heartbeat per channel even with concurrent turns mapped to it;
  respect Retry-After on 429 (skip a beat, never tight-loop).
- No config knob unless review demands one — this is a fix, not a feature.

## Scenarios (to write before todo, in isaac-discord's harness)

- a turn lasting > 2 beats sends ≥3 typing requests, spaced ~8s (fixture
  clock).
- heartbeat stops at turn end: no typing requests after on-turn-end
  (success AND error outcomes).
- 429 with Retry-After on the typing route: next beat waits, turn
  unaffected.

Draft until scenarios are committed @wip. Independent of isaac-5nxf —
dispatchable as soon as planned.



## Scenarios (committed @wip at slagyr/isaac-discord ba9b997)

features/comm/discord/typing.feature — :33 refresh while running (wait:true
+ clock 17s ⇒ 3 POSTs); :46 stops at turn end (fast turn, 30s ⇒ 1 POST);
:59 error turn also stops (the leak case; relies on guaranteed
finalization). Preamble's aspirational refresh claim corrected.

## Step ledger

| step | status |
|------|--------|
| Grover setup in / faked Gateway / config: / client ready as bot / responses queued (wait) / MESSAGE_CREATE / test clock advances / outbound request matches | reuse |
| **{n:int} Discord outbound HTTP requests to {url:string} were made** | **NEW — count variant; tolerate singular/plural** |

## Design pins

Per-channel scheduler heartbeat: start on-turn-start, refcount concurrent
turns per channel, cancel on-turn-end (every outcome), ~8s period. 429/
Retry-After skips a beat (unit-spec obligation, not Gherkin). No config knob.

## Acceptance

Remove @wip from the three scenarios, then:
    bb features features/comm/discord/typing.feature
    bb spec spec/isaac/comm
Full module bb features + bb spec green. Today's Comm protocol — NO
isaac-5nxf dependency; do not migrate the protocol here.

## Verify fail (attempt 1, 2026-09-03): bean branch regresses discord comm specs and does not satisfy the full-module green gate

## Verify fail (attempt 2, 2026-09-03): typing heartbeat scenarios pass, but discord comm specs still regress and the full-module green gate remains unmet
