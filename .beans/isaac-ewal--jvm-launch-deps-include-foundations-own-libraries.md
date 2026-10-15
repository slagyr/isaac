---
# isaac-ewal
title: JVM launch deps name foundation as a local dependency
status: in-progress
type: task
priority: normal
tags:
    - foundation
created_at: 2026-10-15T15:48:17Z
updated_at: 2026-10-15T16:27:12Z
---

## Problem

`isaac modules deps --edn` (`isaac.foundation.module.loader/config->launch-deps`) emits every module coord plus foundation's source dir as a bare `:paths` entry. Two things go wrong:

1. **Foundation's own libraries are missing** (babashka/process, cheshire, clj-yaml, c3kit apron, tools.cli, tools.deps.alpha). Under bb they come from the keg's `libexec/bb.edn`, so the gap never showed. On the JVM, `isaac service install --runtime jvm` installs a service that cannot start: `Could not locate clojure/tools/gitlibs__init.class` (or `clojure/tools/cli`).
2. **Every JVM launch warns** `WARNING: Use of :paths external to the project has been deprecated, please remove: …/isaac-foundation/src`. The clojure CLI treats a source path outside the current project as deprecated; a future CLI may refuse it.

## Fix

Put foundation in `:deps` as a dependency on its own directory instead of a bare source path:

```clojure
{:deps {io.github.slagyr/isaac-foundation {:local/root "<foundation dir>"}
        …module coords…}}
```

The clojure CLI then reads foundation's `deps.edn` for its source and its libraries — one source of truth — and no external `:paths` remains. A top-level dep is not pruned by the modules' `:exclusions` (those only cut transitive copies).

The brew keg must ship that `deps.edn`: today `slagyr/homebrew-tap` `Formula/isaac.rb` installs only `libexec`, `src`, `resources`. Add `deps.edn` to `root.install`.

## Scope

- isaac-foundation: `config->launch-deps` (and `foundation-seed-path` → the foundation dir), `modules deps` help text, the new step.
- slagyr/homebrew-tap: `Formula/isaac.rb` installs `deps.edn`.

## Acceptance

- Scenarios in isaac-foundation `features/module/modules_deps_emit.feature` (two @wip, see the gate lines below):
  - "--edn names foundation as a local dependency" — fast.
  - "the emitted --edn deps boot isaac from the Isaac root" — @slow, run with `bb features-slow`; boots with no deprecation warning.
- New step `a JVM in the Isaac root boots {args:string} from the emitted launch deps`: like the existing `the emitted launch deps boot {args:string}`, but the JVM's working directory is the Isaac root (`g/get :root`), not the checkout. Mind the phrase: anything starting "the emitted launch deps boot …" is swallowed by the existing step.
- One-time, not a scenario: the formula change is in, and `brew install --HEAD slagyr/tap/isaac` (or the formula's own test) leaves `libexec/isaac-foundation/deps.edn` in the keg.

## Workaround in place

On 2026-10-15 skiff's server moved to the JVM with a hand-made launcher that merges the keg `bb.edn` `:deps` into the `modules deps --edn` map. Once this lands and the keg is upgraded, reinstall with `isaac service install --runtime jvm` and delete the launcher (deploy is Micah's call).

## Planner notes

- Why the existing boot scenario passes today: it boots from the checkout, whose `deps.edn` supplies foundation's libraries, and the marigold.app fixture drags them in through `{:local/root "../.."}`. The new scenarios use `{:modules {}}` for that reason.
- Planner already dropped the `:paths`/`/src` assertions from the two existing `--edn` scenarios (c3d29cc) — they describe the old shape.
- Out of scope: `libexec/bb.edn` still repeats foundation's `:deps` for bb.

feature-baseline: isaac-foundation c3d29cc72119de841fd5839700ea14f0d145f1c6
feature-blob: isaac-foundation features/module/modules_deps_emit.feature f1b91df0f0c78884fc668a75e9b9fb776a44c922
