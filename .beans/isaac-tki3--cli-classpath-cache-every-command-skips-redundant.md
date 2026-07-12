---
# isaac-tki3
title: 'CLI classpath cache: every command skips redundant startup planning (clic-2)'
status: todo
type: feature
priority: high
created_at: 2026-07-12T20:38:59Z
updated_at: 2026-07-12T20:38:59Z
---

## Goal (Micah, 2026-07-12)

'I want the classpath to be cached so that commands are snappy and they don't waste time/compute on redundant tasks.' Every isaac command — not just the --help/--version fast paths isaac-clic shipped — reuses cached startup work. Crews shell out to isaac constantly (hundreds of execs per bean), so startup tax is multiplied across the fleet.

## Design

1. **Persist the classpath plan** in cache/cli.edn alongside the existing commands list: the output of plan-module-classpath-pairs (module [id coord] pairs / the deps map fed to add-deps). On a cache hit, ANY command feeds the cached plan straight to composition — skipping module walking, manifest reads, and deps resolution.
2. **Fail-open, always**: any failure on the cached path (stale coords, missing .gitlibs artifact, deserialization error) silently falls back to the full replan and rewrites the cache. Worst case is the old slow path; a stale cache must never break the CLI.
3. **Enumerated invalidation inputs** in :basis — not just config-file mtimes: the isaac/foundation version itself (every brew upgrade invalidates), the module SHA pins in isaac.edn, and every config file that feeds the plan. The cache is sound because plans are deterministic from local SHA-pinned state; the basis must record exactly which inputs it watched.
4. **Measure first and record**: instrument startup phases (plan / compose / boot) and capture cold-vs-warm timings for a real command (e.g. isaac config keys providers) BEFORE and AFTER, in the bean notes. Acceptance target: the planning phase substantially eliminated on warm runs (aim >=50% of its cold cost); if measurement shows planning is a trivial slice of boot, STOP and report — do not add complexity for noise.

## Out of scope

Caching the registered command table for dispatch (running a command needs module code loaded anyway; clic already covers help/routing). Revisit only if post-plan-cache measurements show berth-walking still significant.

## Scenarios (worker writes; required coverage)

1. Warm cache: a real (non-fast-path) command composes from the cached plan — the planning functions are NOT invoked (recording stub/spy), output identical to cold run.
2. Any watched input changes (config mtime, version) -> full replan, cache rewritten.
3. Cached path failure (e.g. plan references a missing artifact) -> silent full replan, command succeeds, cache refreshed.
4. Timing evidence recorded per design point 4 (one-time acceptance note, not a permanent scenario).
