---
# isaac-iq1t
title: unify module discovery via classpath manifest scan (drop hardcoded resources/ path)
status: in-progress
type: bug
priority: normal
created_at: 2026-05-19T18:49:04Z
updated_at: 2026-05-21T00:22:41Z
---

## Problem

`isaac.module.loader/discover-local-root` (src/isaac/module/loader.clj:141) reads the manifest from a fixed filesystem path:

```clojure
manifest-path (str root "/resources/isaac-manifest.edn")
```

`discover-resolved` (loader.clj:164) does it differently — it adds the coord's deps to the classpath, then uses `manifest-resource` to scan the classpath for any `isaac-manifest.edn` whose `:id` matches.

Two consequences:

1. **Convention mismatch.** Every module in this repo (`telly`, `kombucha`, `echo`) puts its manifest at `resources/isaac-manifest.edn`, but isaac core itself puts it at `src/isaac-manifest.edn`. Consumer repos like `isaac-discord` that ship an `isaac-manifest.edn` from `src/` cannot use `:local/root "."` — `discover-local-root` looks in the wrong place.
2. **Filesystem-only path.** The hardcoded `resources/isaac-manifest.edn` is meaningful for on-disk modules only. Once a module is jar-packaged (Maven, Clojars, even a git-fetched dep with a custom `:paths`), the filesystem layout disappears and classpath scanning is the only working approach. So the fixed path is brittle by design.

## Fix

Collapse `discover-local-root` and `discover-resolved` into a single path. Both should:

1. Resolve the coord's `deps.edn` so its `:paths` (and transitive deps) land on the classpath via `add-module-deps!`.
2. Use `manifest-resource` to find the manifest whose `:id` matches the requested module.

Sketch:

```clojure
(defn- discover-one [context id coord]
  (cond
    (= core-module-id id)
    (if-let [entry (get (core-index) core-module-id)] ...)

    :else
    (do
      (when (seq coord) (add-module-deps! id coord))
      (let [resource (manifest-resource id)]
        ...))))
```

The branch on `(:local/root coord)` goes away — `add-module-deps!` already handles `:local/root` coords by adding them to the classpath through tools.deps; no special filesystem-reading helper is needed.

`local-root-path` (loader.clj:65) might still be useful for error messages ("local/root path does not resolve") and for storing `:path` on the discovered entry; review whether to keep it.

## Acceptance

- [ ] `discover-local-root` removed; one discovery path for local-root and resolved coords alike.
- [ ] A module whose manifest lives at `src/isaac-manifest.edn` (not `resources/`) is discoverable via `:local/root "."`. Add a feature scenario covering this in `features/modules/coordinates.feature`.
- [ ] Existing scenarios using `:local/root "modules/isaac.comm.telly"` (telly's manifest still at `resources/isaac-manifest.edn`) keep passing.
- [ ] Validation errors remain useful — e.g., `:local/root` pointing at a missing dir, or a present dir whose `deps.edn` exists but doesn't contribute a matching manifest.
- [ ] `bb spec && bb features` green.

## Motivation

Unblocks `isaac-discord`'s 4 failing scenarios. Today it can't reach its own discord manifest via `:local/root "."` because the manifest is at `src/isaac-manifest.edn` (where core puts its manifest) rather than `resources/isaac-manifest.edn` (where the loader looks for module manifests). After this fix, any layout that places the manifest on the module's classpath works.

## Out of scope

- Adding classpath-wide auto-discovery (scanning every `isaac-manifest.edn` on the classpath whether declared in `:modules` or not). Already discussed and rejected — modules must be explicitly declared to be activated.



## Verification failed

HEAD: f959ceda3e71610ca5f103b9f39db2469a7ad23b
Working tree: clean

1. Full `bb features` on this branch emits repeated stray output: `Error building classpath. Manifest file not found for isaac.comm.discord/isaac.comm.discord ...`. Likely from `src/isaac/module/loader.clj` now routing `:local/root` discovery through `add-module-deps!` before manifest lookup.
2. Feature-suite speed regressed sharply on this branch: 83.83s / 653 examples = 128.38 ms/example, versus `main` on the same machine at 15.55s / 652 examples = 23.84 ms/example. This bean changes shared feature-test infrastructure, so the slowdown is blocking.

Targeted bean checks passed: `bb spec spec/isaac/module/loader_spec.clj` and `bb features features/modules/coordinates.feature`. Full `bb spec` is also green. Full `bb features` still has 8 pre-existing failures on `main`, so those failures were not counted against this bean.



## Verification failed

HEAD: af7486cf3142066f6fabd96e708eb6bdb3c57e6f
Working tree: clean

1. `src/isaac/module/loader.clj` now checks `manifest-resource` before `ensure-module-deps!`. If a matching manifest for the same module `:id` is already on the JVM classpath, discovery can select that stale manifest and skip loading the declared coordinate entirely. That regresses the old behavior, which always loaded the declared deps first.
2. The new test/feature coverage stubs out the production classpath path (`spec/isaac/config/config_steps.clj`, `spec/isaac/marigold.clj`), so the acceptance now passes even if real tools.deps loading or real classpath scanning is broken.

What is correct: current targeted specs/features are green in a clean clone, but the production discovery path is still under-tested and can return the wrong manifest when the same id already exists on classpath.
