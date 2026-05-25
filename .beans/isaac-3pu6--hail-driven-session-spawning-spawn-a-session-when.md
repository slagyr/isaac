---
# isaac-3pu6
title: 'Hail-driven session spawning: spawn a session when a reach-one delivery has no idle candidate'
status: draft
type: feature
created_at: 2026-05-25T01:01:23Z
updated_at: 2026-05-25T01:01:23Z
parent: isaac-ugx7
---

Deferred from isaac-wte9 (hail delivery worker).

Today, a reach-one delivery whose candidate pool has no idle session waits; an empty pool (no matching session at all) ends as undeliverable. This bean would instead **spawn a new session** for a matching crew and deliver the hail to it.

## Open questions (refine before work)
- Which crew to spawn under when the frequency is tag/band-addressed across multiple crews?
- Session naming / origin marking for spawned sessions.
- Opt-in: should spawning be a per-band / per-frequency flag rather than default?
- Interaction with crew :max-in-flight capacity.

## Relationship
- Parent: isaac-ugx7 (Hail epic).
- Extends isaac-wte9 (delivery worker), which currently waits or marks undeliverable.
