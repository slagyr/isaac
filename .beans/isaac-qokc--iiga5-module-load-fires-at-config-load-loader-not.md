---
# isaac-qokc
title: 'iiga(5): module load fires at config-load (loader), not server start'
status: done
type: task
priority: normal
created_at: 2026-06-16T00:59:07Z
updated_at: 2026-06-16T01:30:00Z
---

Child of epic isaac-iiga. Closes the gap the rename (n4dj) exposed: who loads modules.

## Problem
Today module instantiation + the load hook (on-load, post-n4dj) live in start-modules! (loader.clj:617),
whose ONLY caller is server/app.clj:199. So module LOAD is server-only — which contradicts the model
(load = presence, side-effect-free, safe on ANY invocation incl. CLI). After n4dj's pure rename, on-load
still fires only at server boot.

## Change
Move module instantiation + on-load/on-unload OUT of the server-only start-modules! INTO the loader's
activate/discovery path (discover! / activate! / process-manifest-berths!), so it fires wherever config loads
(CLI, agent runtime, server). The loader owns load/unload; the server owns ONLY Service start/stop.
- Keep lazy activation (user modules activate on first use via comm-factory) — load-on-use stays valid; this is
  about load no longer being server-GATED.
- Decide during the work: is Module/on-load doing real work after the split, or is it largely subsumed by berth
  activation (with anything resource-y becoming a Service via kbzd's berth)? Likely most on-load impls become
  no-op; keep the protocol but don't put resources there.

## Acceptance
- Module on-load fires at config load (assert: a fixture module's on-load fires on a CLI / non-server invocation
  that loads config) — NOT only at server boot.
- on-unload fires on module removal/unload.
- NO service/resource starts as a result of load (that stays Services, server-only) — preserves the
  discord-loads-but-doesn't-connect-on-CLI guarantee.
- All affected suites green.

## Deps
- n4dj (rename) — done. kbzd (Service primitive) — done; the resource-y-becomes-Service decision rides on it.
- Blocks iiga(4) isaac-95lv (dedup) — both modify the boot/load path; do the relocation before collapsing it.
