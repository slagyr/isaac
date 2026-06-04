---
# isaac-fzsz
title: :deps resolution via tools.deps / babashka internals
status: todo
type: feature
priority: normal
created_at: 2026-06-04T14:10:52Z
updated_at: 2026-06-04T14:11:09Z
parent: isaac-brth
blocked_by:
    - isaac-htkp
---

A consumer manifest's `:deps` declares modules it requires. The
foundation resolves them using `tools.deps`/babashka primitives — if
the declared module is already present in user `:modules` or cached
in `~/.gitlibs`, no fetch. Otherwise the coordinate is resolved
through normal `tools.deps` machinery. Resolved modules become part
of the module-index just as if the user had declared them in
`:modules`. If the coordinate can't be resolved, config-load errors.

## Why delegate

tools.deps handles git fetching, classpath construction, transitive
resolution, caching (`~/.gitlibs`), and version conflict rules.
Reimplementing any of that is a tar pit. Bean F is a thin shim:
\"call existing fns; surface their errors.\"

## Behavior

1. After manifest shape validation (isaac-htkp), foundation collects
   every loaded module's `:deps`.
2. For each declared dep:
   - If the module-id is already in the working module-index (user
     installed it via `:modules` OR another module's `:deps` pulled
     it in earlier), skip.
   - Otherwise hand the coordinate to `tools.deps`/bb to resolve,
     locate, and parse the module's manifest.
   - Add the resolved module to the module-index.
3. The transitive closure becomes the loaded set. Order: deps before
   dependers (isaac-f77b uses this for `on-startup`).
4. Coordinates that can't be resolved (bad `:local/root`, network
   failure on a `:git/url`, unknown shape) surface as config-load
   errors naming the consumer module and the offending dep.

## Test policy: no remote git in scenarios

Tests use `:local/root` coordinates only. The git-fetch path is
covered by tools.deps' own test suite; isaac's job is the delegation
contract. If we ever need to assert network behavior in our own
specs, we stub the resolver — never reach for real URLs in a test.

## Feature

`features/module/deps_resolution.feature` — two `@wip` scenarios:

- happy: consumer's `:deps` auto-resolves; transitively-loaded module
  appears in module-index even though user only installed the
  consumer in `:modules`.
- error: unresolvable `:local/root` errors at config-load.

## Acceptance

- Remove `@wip` from `features/module/deps_resolution.feature`.
- `bb features features/module/deps_resolution.feature` passes.
- isaac-htkp's scenarios still pass — bean F adds resolution behavior
  on top of htkp's preservation behavior; both should coexist.
- No new outbound network requests during `bb spec` or `bb features`.

## Out of scope

- Version conflict resolution between two consumers requiring the
  same provider at different coordinates. tools.deps has rules for
  this; if isaac needs to override, that's a later bean.
- Lockfile generation. Future concern.
- Coordinate compatibility checks (\"this consumer needs `:git/sha
  abc`; user installed `:git/sha def`\") — tools.deps already
  resolves to ONE sha; whether to warn on mismatch is a UX question
  for a later bean.

## Dependencies

- Blocked by isaac-htkp (need `:deps` parsed and stored before
  resolving it).
- NOT blocked by anything else in the wave — runs alongside isaac-yb39,
  isaac-c2g5, isaac-2yqb, isaac-f77b.

## Notes for the worker

- tools.deps entry points worth scanning before starting:
  `clojure.tools.deps/resolve-deps`, `make-classpath-map`, the
  `procurer` multimethod (handles `:local/root`, `:git/url`+`:git/sha`,
  `:mvn/version`, etc.).
- In babashka, equivalents live under `babashka.deps` /
  `babashka.classpath`. Pick whichever surface the rest of isaac is
  using today; don't introduce a new abstraction layer just for this.
- Surface a structured error per failed coordinate: `{:module-id …
  :coordinate … :reason …}`. Don't let raw tools.deps stack traces
  bleed into the user's config-error report — wrap them.
- Cycles among `:deps` (A → B → A) should error explicitly with a
  named cycle, not loop forever or get masked as an unrelated
  resolution failure.
