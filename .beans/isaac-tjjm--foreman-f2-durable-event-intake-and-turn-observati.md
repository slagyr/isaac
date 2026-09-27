---
# isaac-tjjm
title: 'Foreman F2: durable event intake and turn observations'
status: draft
type: feature
priority: normal
created_at: 2026-09-27T22:33:11Z
updated_at: 2026-09-27T23:09:08Z
parent: isaac-q3u3
---

Likely repo: **isaac-foreman** (with its HTTP route contribution). Design: Micah + planner, 2026-09-27. Builds on completed F1 (isaac-mjr4) and the existing turn-observer seam (isaac-bbov).

## Contract to plan

- The crew `signal` tool, `POST /foreman/events`, CLI signal, and turn observers write a common event envelope to one durable intake before acknowledging it.
- Event IDs deduplicate retries; consuming an event transitions one machine instance at most once. Startup resumes unconsumed events. State/history remain Foreman's authority; beans may be a later view, not the store.
- Observe turn start/end/death and carry source request ID plus machine/instance identity. A turn that ends without an expected semantic signal yields a backstop event the machine can handle.
- Keep events independent of Hail delivery and session selection. Preserve F1's unhandled-event history behavior.

## Scenario plan to review

1. Tool and HTTP send the same event shape; acknowledged events survive restart.
2. Repeated event ID changes state once and is visible in history.
3. A turn ending without its signal produces a handled backstop transition.
4. An unhandled observation is recorded without corrupting instance state.

Draft until scenarios are committed and baselined. Beans mirroring remains deferred as recorded in isaac-tdgt.
