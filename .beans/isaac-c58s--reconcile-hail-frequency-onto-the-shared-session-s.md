---
# isaac-c58s
title: Reconcile hail frequency onto the shared session selector
status: draft
type: feature
priority: normal
created_at: 2026-06-26T16:28:54Z
updated_at: 2026-06-26T16:29:07Z
parent: isaac-4e4b
blocked_by:
    - isaac-nbgn
---

Child of isaac-4e4b — LAST, deliberately. Make hail's :frequency BE the shared :select map (or map cleanly onto it) from isaac-nbgn (B1), so hail stops parsing its own selector and consumes the shared agent-side resolver. hail keeps its extras: --band (named/saved selector), --reach all (fan-out — the others are :one), --payload + async delivery.

## Why last
Refactors WORKING code just shipped (ebm2 stable-id lifecycle + kt1m :crew selector). Highest risk, lowest new value of the four migrations. Do it only after the shared lib is battle-tested on prompt/chat/acp.

## Scope
- hail send builds the shared :select from --session/--crew/--session-tag (already has these) + --spawn (== existing :spawn-session) + --reach; serializes it as :frequency.
- The router resolves via the shared resolver (or the shared resolver is what the router already does — converge them).
- --with-* override on hail (set the spawned/targeted session's params). Reconcile with hail's current behavior.
- Keep band/reach-all/payload/async.

## Acceptance
- hail's selector logic == the shared one (no hail-specific duplicate); :frequency is the serialized :select.
- All hail routing/delivery features still green (ebm2/kt1m scenarios).
- hail-specific extras (band, reach all, payload, async) preserved.

Blocked by isaac-nbgn (B1). Surfaced 2026-06-26.
