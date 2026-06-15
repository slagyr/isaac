---
# isaac-zlfp
title: Canonicalize isaac.session.store.spi (dedup agent/server divergence)
status: in-progress
type: task
priority: normal
created_at: 2026-06-15T22:21:05Z
updated_at: 2026-06-15T22:29:10Z
---

acp off-monolith prereq. acp will depend on isaac-agent + isaac-server (foundation transitive); it requires
isaac.session.store.spi, which BOTH agent and server ship — and they have DIVERGED:
- agent's copy == the monolith (carve 4d486f1, 2026-06-14)
- server's is newer (e115dfa, 2026-06-15): different factory-resolution path (test-store-stub ordering fix;
  lineage of completed isaac-y21w)
Verified: spi differs by 17 lines. Depending on both puts two different spi on the classpath; which loads is
nondeterministic. (isaac.comm.protocol and isaac.comm.registry are byte-identical across agent/server — benign;
only spi is the real conflict.)

## Direction (planner-reviewed): canonical home is isaac-AGENT, NOT foundation.
Session storage is AGENT domain (sessions/transcripts are the agent runtime); foundation is CLI/loader/config/
nexus. Do NOT move spi (or the store impls) to foundation — that re-introduces the exact agent-concepts-in-
foundation layering violation the extraction cleanup just removed. The store impls (memory/sidecar/index — 4
files) already live in agent only; keeping spi WITH its impls in agent makes the impl->ns + (require) gotcha
disappear.

## Step 1 (decides the fix): why does server ship spi with ZERO impls?
- (a) vestigial / test-stub-only (the "test-store-stub" lineage suggests this) -> server DROPS its spi copy;
  agent is canonical; acp -> agent; collision gone. Simplest; no new module, no impl move.
- (b) genuinely needed at server runtime -> resolve via the server<->agent relationship (server is currently a
  sibling, depends-on agent: no): either server -> agent, or a small shared session-store module. NOT foundation.

## Stopgap (to unblock acp sooner, if single-home is deferred)
Reconcile the two variants byte-identical (backport server's fix into agent + monolith); duplication becomes
benign like comm.protocol/registry. Eventual single home is still AGENT.

## Acceptance
- Exactly one canonical isaac.session.store.spi behavior (no divergence).
- acp can declare deps on agent + server with no classpath collision on session.store.spi.
- All affected suites green: foundation, agent, server, acp (+ hail/hooks which consume spi via agent).

## Impact / consumers
agent 43 files, server 3, acp 4, hail 5, hooks 2, monolith 51.

## Relationships
- Prerequisite for the acp off-monolith migration (next acp bean).
- Related to iiga(4) isaac-95lv (dedup reconcile) but DISTINCT — 95lv does NOT touch session.store.spi.
