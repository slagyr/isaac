---
# isaac-yrae
title: Untitled
status: todo
type: task
created_at: 2026-06-27T17:40:26Z
updated_at: 2026-06-27T17:40:26Z
---

---
# isaac-hsid
title: 'Hail delivery must not reference or depend on session sidecars (impl detail of store)'
status: todo
type: bug
priority: high
created_at: 2026-06-27T18:00:00Z
updated_at: 2026-06-27T18:00:00Z
---

## Summary
Hail routing/delivery should only use the abstract session store SPI. Sidecar files (.edn + .jsonl) are an internal detail of the :jsonl-edn-sidecar store impl and session persistence. Hail code must not know about them.

## Problem
- Delivery logic (delivery_worker, router) ends up coupled to sidecar concepts for matching/spawn/bind/live vs persisted.
- Creating sidecar (e.g. via sessions set or metadata) does not make session a hail recipient; leads to :no-recipients.
- Violates encapsulation: changing store impl (sidecar vs other) should not affect hail.
- Related: verify hails undeliverable even when sidecar metadata matches crew/tags.

## Root cause
Hail delivery interacts with session-store but also has paths that assume or leak sidecar storage (e.g. file-based matching, when sidecar becomes "deliverable", reliance on persisted vs in-memory state for bands).

## Acceptance criteria
- Audit and remove all sidecar-specific knowledge from isaac-hail (use only store/list-sessions, get-session, in-flight?, etc.).
- Ensure sidecar concerns stay in isaac-agent/src/isaac/session/store/sidecar (and spi).
- Session activation (sidecar creation) must integrate with delivery so matching bands can deliver to it.
- Create follow-up beans as needed for: decoupling store from hail, live vs sidecar activation for bands, band frequency matching using abstract store only.
- Update tests for delivery without sidecar assumptions.
- Clean up related in router, spawn, ACP, etc.
