---
# isaac-qomx
title: 'Discord typing heartbeat: re-ping while a turn is in flight'
status: draft
type: feature
priority: high
created_at: 2026-08-31T16:03:45Z
updated_at: 2026-08-31T16:03:45Z
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
