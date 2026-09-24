---
# isaac-600d
title: 'isaac-foundation: reading the config snapshot must not register a nil config — snapshot is read-only, only install registers'
status: completed
type: bug
priority: high
tags:
    - foundation
    - config
created_at: 2026-09-21T18:17:32Z
updated_at: 2026-09-24T23:51:21Z
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

## Worker notes (scrapper@isaac-work-2, 2026-09-24)

Branch isaac-foundation `bean/isaac-600d`, rebased on main b3db42f:
- a0ae77d: snapshot is read-only; only set-snapshot! registers the slot (prior worker's b95f04e, rebased)
- 9d07177: feature harness owns the config slot at scenario start

**Why 9d07177:** with only the loader fix, the one-time rxun check went RED
(3 deterministic failures in isaac-agent `llm/mcp_turn_registry.feature`,
"unknown tool: exec__run"). The control run at 22694fc was green. Root cause:
`nexus/-with-nested-nexus` shares only atoms that already exist in the outer
nexus. `root-steps/initialize-root!` resets the nexus to `{}`. Before this
bean, an early snapshot read planted `:config` in the outer nexus, so a
`set-snapshot!` inside `with-feature-fs` (nested) reset a shared atom. After
the fix, the slot was registered only in the nested map and was lost when
the scope exited. The fix makes `initialize-root!` register `[:config] (atom nil)`,
the same way `nexus/init!` does in production (runner/main already init! before
any nested install). Spec: spec/isaac/foundation/root_steps_spec.clj.

**Acceptance results:**
- `bb spec spec/isaac/config/loader_spec.clj`: 19 examples, 0 failures
- `bb ci`: specs 1273/0. Features: 229 with 4 `modules pins` failures, the same 4
  on main b3db42f in this environment. `~/.gitlibs/_repos/file/REL/fixture-agent`
  origin points at a deleted work-1 worktree, so this is shared-cache pollution,
  not this bean. One run was fully green (229/0) before the cache got polluted.
  Occasionally one gitlibs-touching scenario flakes (discovery / registry
  install); both pass in isolation.
- `bb jvm-spec`: 8 failures, the same 8 on main b3db42f (module lifecycle /
  protocol no-op hooks). Tracked by isaac-jf80.
- One-time rxun check: isaac-agent @ cc44840 with foundation at the 22694fc
  lineage + both 600d commits (b95f04e + cherry-pick 8b730fc, via :local/root):
  `bb features` 838 examples, 0 failures, **twice in a row**. The same agent against
  foundation main b3db42f *without* 600d fails 32 scenarios, and 32 with 600d:
  that's old-agent-vs-new-main drift, unrelated. So the main-sha pin itself
  can't be green for cc44840, and the proof was done on the rxun lineage.

## Verify (perceptor@isaac-verify, 2026-09-24): PASS

- Diff matches the bean: `snapshot` is `(some-> (nexus/get :config) deref)`; only `set-snapshot!` (via `install-config-atom!`) registers the slot and reuses an existing atom; `unresolved-ref/1` returns nil on a fresh nexus. The harness change (`initialize-root!` registers `[:config]`, as `nexus/init!` does in production) is justified by the nested-nexus finding and covered by root_steps_spec.
- `bb spec` loader_spec + root_steps_spec: 21/0.
- Branch (base b3db42f): `bb ci` specs 1273/0; features 4 fail in cli/modules_pins.feature, reproduced identically on origin/main b3db42f (gitlibs fixture-agent origin points at a deleted work-1 worktree; environmental). `bb jvm-spec` 8 failures, the same 8 on main (isaac-jf80).
- One-time rxun check, reproduced independently: isaac-agent cc44840 + foundation 22694fc with both 600d commits cherry-picked (:local/root), `bb features` 838 examples, 0 failures, twice in a row. Pinned to the rxun lineage, not main, because of the old-agent-vs-new-main drift the worker described; the intent (hazard gone at the source) holds.
- main moved to e6ba68f (isaac-j4jr) before landing. Re-ran `bb ci` on the squash commit: specs 1278/0, features 229/0, lint-pins ok, EXIT 0.

## Landed on main (2026-09-24)
main-sha: isaac-foundation 3da008fc7a9d53012935a9a521f13c51f5912c09
