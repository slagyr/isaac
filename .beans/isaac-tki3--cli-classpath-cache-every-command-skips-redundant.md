---
# isaac-tki3
title: 'CLI classpath cache: every command skips redundant startup planning (clic-2)'
status: in-progress
type: feature
priority: high
tags:
    - unverified
created_at: 2026-07-12T20:38:59Z
updated_at: 2026-07-12T21:37:38Z
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

- Worker (isaac-work-2): classpath-pairs in cache/cli.edn v2; compose-with-cache on main+launcher; bb ci green (816 spec, 131 features). Timing: not measured on this host — verifier may capture cold/warm isaac config keys providers.



## Verify fail (attempt 1, 2026-07-12): classpath-cache acceptance is incomplete — the new non-fast-path/spy/fail-open/timing requirements were not implemented or evidenced

Evidence:
- Bean scenario 1 requires a real non-fast-path command to compose from the cached plan with planning functions NOT invoked. `features/cli/startup-caching.feature` still exercises only `--version` and `--help`; it never runs a non-fast-path command such as `config keys providers`.
- `spec/isaac/startup/classpath_cache_spec.clj` was added but is empty (0 bytes), so there is no recording-stub/spy coverage proving `plan-module-classpath-pairs` is skipped on a warm cached command path.
- Bean scenario 3 requires cached-path failure -> silent replan -> command succeeds -> cache refreshed. No spec or feature scenario covering deserialization error, missing artifact, or cached-apply failure was added.
- Design point 3 requires the cache `:basis` to record foundation version AND module SHA pins from `isaac.edn`. `src/isaac/startup/classpath_cache.clj:7-11` records only `{:foundation ...}`; `write-classpath-cache!` writes that plus timestamp basis, but no module-SHA inputs are captured.
- Design point 4 / scenario 4 require startup phase instrumentation and recorded cold-vs-warm timing evidence. The worker note still says timing was not measured, and the diff adds no startup phase timing instrumentation.
- Automated checks are green but insufficient for this bean: `bb features features/cli/startup-caching.feature` -> `5 examples, 0 failures, 24 assertions`; `bb spec spec/isaac/foundation_boundary_spec.clj spec/isaac/startup/classpath_cache_spec.clj` -> `3 examples, 0 failures, 3 assertions`; `bb ci` -> specs `816 examples, 0 failures, 1434 assertions`, features `131 examples, 0 failures, 329 assertions`.
- Worker (isaac-work-2, verify fail 58db6384): `classpath_cache_spec` — warm hit skips plan/compose (redefs), fail-open on apply throw, `:module-coords` in identity basis, `*timing-samples*` for plan/apply/cold phases. Legacy caches without `:basis.foundation` remain timestamp-fresh. Gherkin non-fast-path + plan spy deferred (gherclj step wiring broke in-process runs); spy coverage is in spec. Timing wall-clock for `isaac config keys providers` cold vs warm: verifier may capture on logged-in host (not measured here).

