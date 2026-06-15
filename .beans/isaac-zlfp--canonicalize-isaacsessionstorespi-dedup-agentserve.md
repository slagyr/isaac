---
# isaac-zlfp
title: Canonicalize isaac.session.store.spi (dedup agent/server divergence)
status: draft
type: task
priority: normal
created_at: 2026-06-15T22:21:05Z
updated_at: 2026-06-15T22:21:05Z
---

Child/sibling of the acp off-monolith migration. acp will depend on isaac-agent + isaac-server (foundation transitive); it requires isaac.session.store.spi, which BOTH agent and server ship — and they have DIVERGED:

- agent's copy == the monolith (carve 4d486f1, 2026-06-14)
- server's is newer (e115dfa, 2026-06-15) with a different factory-resolution path (test-store-stub ordering fix; lineage of completed isaac-y21w)

Depending on both puts two different isaac.session.store.spi on the classpath; which loads is nondeterministic. (isaac.comm.protocol and isaac.comm.registry are byte-identical across agent/server — benign; only spi is a real conflict.)

## Proposed change (recommended)

Move isaac.session.store.spi to isaac-foundation (lowest shared dep — both agent and server already depend on it), reconciled to the server variant (newer). Remove agent's and server's copies. Consumers keep [isaac.session.store.spi] unchanged (ns relocates, provided transitively).

Design decision for the planner (the gotcha): spi hard-references store impls via impl->ns + (require ns-sym), and the impls (session.store.memory / sidecar / index) live in agent. Moving only spi to foundation breaks those requires. Resolve by either:
- (i) move the impls to foundation too (bigger — session-store subsystem ownership), or
- (ii) invert registration so impls self-register on load and spi holds no hard impl refs (smaller, cleaner SPI).

## Minimal alternative (if relocation is out of scope now)

Reconcile the two variants to byte-identical (backport server's fix into agent + monolith). Duplication becomes benign (like comm.protocol/registry), and acp can depend on both with no conflict. Defer single-home relocation to iiga(4).

## Acceptance

- Exactly one canonical isaac.session.store.spi behavior (no divergence).
- acp can declare deps on agent + server with no classpath collision on session.store.spi.
- All affected suites green: foundation, agent, server, acp (+ hail/hooks which consume spi via agent).

## Impact / consumers

agent 43 files, server 3, acp 4, hail 5, hooks 2, monolith 51.

## Relationships

- Prerequisite for the acp off-monolith migration (next acp bean).
- Related to iiga(4) isaac-95lv (dedup reconcile machinery) but DISTINCT — 95lv collapses config/install, configurator, comm/factory, comm/registry to server boot and does NOT touch session.store.spi.
