---
# isaac-ogiu
title: Classpath cache resolves the real classpath, not just coords (tki3 follow-up)
status: draft
type: bug
priority: high
created_at: 2026-07-13T16:03:13Z
updated_at: 2026-07-13T16:03:13Z
---

## Goal

Cache the RESOLVED classpath so warm isaac commands skip dependency resolution. isaac-tki3 cached the module coord PAIRS (:classpath-pairs) but not the resolved classpath string — so add-deps / tools.deps still resolves coords into a real classpath on every command. Result: warm ≈ cold, the cache saves almost nothing.

## Measured evidence (zanebot, foundation 0.1.23, 2026-07-13)

- cache/cli.edn contains :data {:classpath-pairs [...]} + :basis (config mtime, foundation version, module SHAs) + :commands. NO resolved classpath.
- `isaac config get defaults` timing: cold (post config-touch) 2.606s, warm 2.324s. The ~2.3s warm floor is deps resolution + JVM/bb boot happening every time; the cache shaves only ~0.3s.
- Micah: 'still taking over a second for simple config reads. And I still don't see the classpath in the cache.'

## Why it matters (amplified)

Every crew shells out to isaac/beans constantly (hundreds per bean). A 2.3s floor on every invocation is a large multiplied tax on the whole fleet, and the dominant cost is exactly what tki3 was meant to eliminate.

## Design

1. Persist the RESOLVED classpath (the -cp string / the output of add-deps over the coord pairs) in cache/cli.edn :data alongside the pairs.
2. On a warm hit (basis unchanged), feed the cached classpath straight to launch — SKIP add-deps / tools.deps entirely. This is the expensive step; pairs alone don't help because resolving them is the cost.
3. Fail-open (isaac-tki3 rule stands): any classpath staleness (missing .gitlibs artifact, unresolvable) -> full re-resolve, cache rewrite. A stale classpath must never break or mis-load.
4. Basis unchanged from tki3 (config mtime + foundation version + module SHAs) — deterministic from SHA-pinned state.
5. MEASURE: warm `isaac config get defaults` target well under 1s (JVM/bb boot may be an irreducible floor — if boot dominates after classpath caching, record that and open a separate boot-time bean rather than chase it here).

## Scenarios (worker writes)

1. Warm cache: add-deps / classpath resolution NOT invoked (spy); command output identical to cold.
2. Basis change (config mtime / foundation version / a module SHA): full re-resolve, classpath rewritten.
3. Cached classpath references a missing artifact: fail-open re-resolve, command succeeds.
4. Timing recorded: warm vs cold for a real command, in bean notes.

## Home

isaac-foundation (brew train).
