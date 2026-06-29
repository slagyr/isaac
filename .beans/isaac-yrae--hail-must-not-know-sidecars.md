---
# isaac-yrae
title: Hail delivery must not depend on or know about session sidecars (impl detail of store)
status: completed
type: bug
priority: high
tags: []
created_at: 2026-06-27T18:00:00Z
updated_at: 2026-06-29T15:07:46Z
---

## Summary
Hail routing/delivery logic should interact only with the abstract session store API. It currently (or risks) leaking knowledge of "sidecars" (.edn + .jsonl persistence files), which are an internal implementation detail of the session store (e.g., :jsonl-edn-sidecar).

## Problem
- Delivery logic (delivery_worker, router) ends up coupled to sidecar concepts for matching/spawn/bind/live vs persisted.
- Sidecar creation (e.g. via sessions set or metadata) does not automatically make a session a deliverable recipient for band hails.
- Leads to :no-recipients even when sidecar metadata exists for matching crew/tags.
- Couples hail module to session store impl details, violating separation (hail shouldn't care about persistence format or sidecar vs index).

## Root cause sketch
Hail delivery interacts with session-store abstraction in some places but also has paths that assume or leak sidecar storage (e.g. file-based matching, when sidecar becomes "deliverable", reliance on persisted vs in-memory state for bands).

## Acceptance criteria
- Remove any direct references or assumptions about sidecars in isaac-hail (delivery_worker, router, etc.).
- All session interaction goes through store/ SPI (list-sessions, get-session, in-flight?, etc.).
- Sidecar logic stays encapsulated in isaac-agent session/store/sidecar (and spi).
- Session activation (sidecar creation) must integrate properly with delivery (or document the activation path).
- Create additional beans as needed for related cleanups (e.g., sidecar vs live activation, band matching using only abstract store).
- Update tests for delivery without sidecar knowledge.

## Verification (2026-06-29)
Verified on fetched GitHub heads:

- `isaac-hail` `86329412d081870a7762e6277b1b42d4e031cf94`
- `isaac-agent` `0b7b928b27be21ba5b457d0455083d8e0c3bdd8e`

Proofs were green:

- `isaac-hail`: `env ISAAC_GIT=1 bb spec spec/isaac/hail/router_spec.clj spec/isaac/hail/delivery_worker_spec.clj` -> `25 examples, 0 failures, 54 assertions`
- `isaac-hail`: `bb features features/router.feature` in a real sibling worktree layout with pinned `foundation` / `agent` / `server` -> `16 examples, 0 failures, 64 assertions`
- `isaac-agent`: `bb spec spec/isaac/session/cli_spec.clj` -> `15 examples, 0 failures, 37 assertions`
- `isaac-agent`: `clojure -M:features features/session/mutation.feature` -> `11 examples, 0 failures, 30 assertions`

I also confirmed `rg -n "sidecar" src spec features` is clean in `isaac-hail` on the delivered head. Hail now requires a registered session store and routes entirely through the store SPI, while agent `sessions set` activates missing sessions so hail can see them without any sidecar-specific path.
