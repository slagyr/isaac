---
# isaac-600d
title: 'isaac-foundation: reading the config snapshot must not register a nil config — snapshot is read-only, only install registers'
status: in-progress
type: bug
priority: high
tags:
    - foundation
    - config
created_at: 2026-09-21T18:17:32Z
updated_at: 2026-09-21T18:19:53Z
---

Repo: **isaac-foundation** (`src/isaac/config/loader.clj`, the "Ambient
Config Snapshot" region). Surfaced by isaac-rxun's verify fail
(perceptor, 2026-09-21).

## The bug

A read of the ambient config mutates process state. `snapshot` derefs
`config-atom`, and `config-atom` does this when nothing is registered yet:

```clojure
(defn- config-atom []
  (or (nexus/get :config)
      (let [cfg* (atom nil)]
        (nexus/register! [:config] cfg*)   ;; a READ plants a nil config
        cfg*)))
```

So the first `snapshot` call on a fresh nexus registers an empty atom under
`:config`. A later entry point that expects to own that slot finds it taken,
reads nil config, and — in the case that bit rxun — builds an empty module
index, so manifest-supplied comm kinds vanish from `config schema`. In
production the server installs config before any turn runs, which is why it
was never seen; tests, CLI paths and any early ambient read expose it, and
the failure is order-dependent (rxun's agent suite went red on different
scenarios per run).

## Fix

- `snapshot` is read-only: `(some-> (nexus/get :config) deref)`. It never
  registers anything. Its docstring keeps the "entry points and wake
  boundaries only" contract.
- Only the install path registers the atom: `set-snapshot!` (and therefore
  `load-config!` / `dangerously-install-config!`) creates and registers the
  atom when absent, then resets it.
- `unresolved-ref`'s one-arity stays (it is the entry-point form); it simply
  returns nil on a fresh nexus now instead of poisoning it.

## Specs (`spec/isaac/config/loader_spec.clj`)

- `snapshot` on a fresh nexus returns nil and leaves nothing registered
  under `:config`
- `set-snapshot!` after such a read installs the config and the next
  `snapshot` sees it (the ordering rxun tripped on)
- an existing registered atom is reused by `set-snapshot!`, not replaced
  (anything holding the atom keeps seeing updates)

## Acceptance

```
cd isaac-foundation
bb spec spec/isaac/config/loader_spec.clj
bb ci
bb jvm-spec
```

- One-time: with foundation repinned to this bean's main sha, isaac-agent's
  full `bb features` on rxun's original agent change (isaac-agent
  `bean/isaac-rxun` @ cc44840, before the worker's rework) is green twice
  in a row. That proves the hazard is gone at the source, not merely
  avoided. rxun's agent fix (carry the reason on the provider slice) still
  stands on its own — in-flight code does not read ambient config even when
  the read is harmless.
- Ungated bean (spec-backed, no feature file): hand off `unverified` to
  isaac-verify.



Dispatched: hail d1663229 2026-09-21T18:19:25Z (band isaac-work)

## Re-dispatch (planner, 2026-09-24)

Previous worker stalled after the fix commit on isaac-foundation `bean/isaac-600d` (b95f04e) with no gates run and no handoff. Resume from that branch: run the Acceptance commands, do the one-time rxun cc44840 check, then close out per the gate. Stay on this branch; do not start over.
