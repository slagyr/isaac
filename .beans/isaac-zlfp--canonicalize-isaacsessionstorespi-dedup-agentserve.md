---
# isaac-zlfp
title: Canonicalize isaac.session.store.spi (dedup agent/server divergence)
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-06-15T22:21:05Z
updated_at: 2026-06-15T22:56:16Z
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


## Implemented (stopgap)

Adopted server's canonical create into agent + monolith so isaac.session.store.spi is byte-identical across all three (md5 e2ec67f...). Step 1 finding: server's spi is genuinely runtime-used (config/install.clj register! at boot), NOT vestigial -> option (b); it relies on agent's impls at composition time (cron/hail already dep both agent+server; acp will). Single physical home (agent) deferred to iiga restructure.

Commits: isaac-agent @ fe73c1c, isaac (monolith) @ d85f09c9. server unchanged (canonical source).

Verified GREEN: agent 535/0, monolith 745/0 (1 flaky feature not reproduced in 2 reruns; spec green), hail 50/0, hooks 12/0. server unchanged.

TWO PRE-EXISTING failures, UNRELATED to spi (confirmed by stashing this change):
- acp: red because monolith renamed isaac.root -> isaac.config.root; acp/src still requires isaac.root. This is the acp-off-monolith migration (isaac.root->isaac.config.root code change), the next acp bean. NOT a spi issue.
- cron: FileNotFoundException isaac.server.test-store (server spec-support not on cron feature classpath). Separate cron deps gap.

Acceptance note: spi divergence is eliminated and acp can dep agent+server with no spi collision; acp suite itself stays red until its isaac.root migration (separate bean).
