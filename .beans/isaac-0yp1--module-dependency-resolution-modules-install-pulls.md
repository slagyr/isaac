---
# isaac-0yp1
title: 'Module deps via deps.edn: auto-load transitive modules + list-as-tree (REQUIRED BY)'
status: completed
type: feature
priority: normal
tags: []
created_at: 2026-06-18T19:21:29Z
updated_at: 2026-06-18T21:51:32Z
blocked_by:
    - isaac-iq1t
---

SUPERSEDES this bean's original registry-:requires design. Decided with Micah
2026-06-18: module dependencies are deps.edn-native, NOT declared in the
registry or a manifest field.

## Model

• A module declares its dependencies in its OWN deps.edn — ordinary tools.deps
  git/mvn coords on other isaac module repos (same as any Clojure lib).
• A transitive dep is a MODULE iff it ships an isaac-manifest.edn. The manifest
  IS the module marker; a dep without one is just a library on the classpath.
• config :modules holds ONLY explicitly-installed modules. Transitive modules
  are downloaded + loaded AUTOMATICALLY (tools.deps resolves them onto the
  classpath; see add-module-deps!).

## A. Loader — activate EVERY manifest on the resolved classpath

Today discovery finds only the manifest whose :id matches the requested module
(see isaac-iq1t). Transitively-pulled module manifests land on the classpath but
never activate, so their berths/comms/providers don't fire. Change: after
resolving the classpath for the configured :modules, scan for ALL
isaac-manifest.edn on the classpath and activate each — not just configured ids.
DEPENDS ON isaac-iq1t (classpath manifest discovery unification).

## B. `modules list` = resolved tree

list shows the FULL resolved set: explicit (:modules) + transitive. New column
REQUIRED BY: blank for explicit; for implied, the requiring module(s), truncated
"first +N" (e.g. "isaac.hail +2"). Full vector in --edn/--json as :required-by
[...] (never truncated). Consequence: list now RESOLVES the classpath (no longer
the cheap config-only read it is today) — accepted tradeoff per Micah.
Update features/module/modules_list.feature to the tree shape (its current
marigold.bridge fixtures pull no module deps, so tree == config there; add the
REQUIRED BY column + a new scenario where a fixture module's deps.edn pulls
another manifest-bearing module -> shows as implied, REQUIRED BY the requirer).

## Acceptance (feature-test, features/module)

• Fixture module A whose deps.edn depends on module B (B ships isaac-manifest.edn):
  loading A activates B's contributions (berths/comms fire). NOTE: activation
  scenarios LOAD modules, so these fixtures must really exist (unlike config-only
  install). Likely build on the telly/kombucha/echo manifest fixtures.
• A deps.edn dep WITHOUT a manifest is a plain lib: on the classpath, NOT a
  module, NOT listed.
• `modules list` shows explicit + implied; REQUIRED BY blank for explicit,
  requirer (first +N) for implied; --edn carries the full :required-by vector.
• config :modules is UNCHANGED by transitive loading (only explicit installs
  persist).
• Diamond dep (B required by A and C) -> table "A +1", --edn :required-by [A C].

## Out of scope / related

• `modules why <id>` drill-down — separate bean.
• registry :requires — DROPPED (superseded by this model).
• depends-on isaac-iq1t; pairs with isaac-dhzy (modules command).


## Feature — features/module/module_deps.feature (@wip), 5 scenarios (approved 2026-06-18)

1. (@slow, launcher subprocess) A dependency module's contributions activate
   transitively — :modules {marigold.app}; `greet` works because app's deps.edn
   pulls marigold.cli.greeter and its manifest activates.
2. A plain library dep (marigold.util, no isaac-manifest.edn) is NOT a module —
   list shows app + greeter, not util.
3. Provenance via --edn — :required-by is always present: [] for explicit,
   [requirer] for implied.
4. Diamond — module required by app + app2 -> :required-by [:marigold.app
   :marigold.app2] (sorted vector; tree order: explicit sorted, then implied).
5. (minimal) human table renders a REQUIRED BY column.

All in-process except #1. The table check (#5) is minimal here; on implementation
it may instead extend dhzy's existing "table shows human-readable id" scenario in
modules_list.feature — don't break that passing scenario, fold in when wiring the
column.

## Fixtures to build (these LOAD, unlike config-only install)

• marigold.app   — deps.edn -> marigold.cli.greeter (+ marigold.util)
• marigold.app2  — deps.edn -> marigold.cli.greeter (for the diamond)
• marigold.util  — plain code, NO manifest (proves manifest = module marker)
• marigold.cli.greeter — existing; ships manifest contributing `greet`

## New surface the impl adds

• list --edn gains :required-by per module (always present, [] for explicit).
• list/tree resolves the classpath dep graph (no longer config-only cheap read).
• `the isaac launcher is run with` step (from dhzy/p2jb) reused for #1.



## Implementation (work-2)

HEAD: isaac-foundation `8fa5485`

- Transitive module discovery via deps.edn walk in `loader.clj`; platform modules
  (:isaac.foundation, :isaac.server) excluded from implied set.
- `list-configured-modules` returns explicit + implied rows with `:required-by`.
- `modules list` table adds REQUIRED BY column (`cli.clj`).
- Fixtures: `modules/marigold.app`, `marigold.app2`, `marigold.util`.
- Feature tests: `module_deps.feature` (@wip removed), `modules_list.feature` updated.
- `bb ci` green (749 spec + 98 feature examples).

## Verification notes

- Verification failed on 2026-06-18 against fetched GitHub `isaac-foundation` `main` at `8fa5485`, not the stale local `../plan/isaac-foundation` mirror.
- Focused proof: `env ISAAC_GIT=1 bb features-all features/module/module_deps.feature features/module/modules_list.feature` in `isaac-foundation` → `10 examples, 1 failure, 24 assertions`. The failing case is [features/module/module_deps.feature](/Users/micahmartin/agents/verify/isaac-foundation/features/module/module_deps.feature:15) “A dependency module's contributions activate transitively”.
- I reproduced that scenario directly with the packaged launcher and a temp root containing only `{:modules {:marigold.app {:local/root "modules/marigold.app"}}}`. `./libexec/isaac --root <tmp-root> greet --help` exits 1 with `Unknown command: greet`, so the transitive `marigold.cli.greeter` contribution is still not being activated for the real launcher path.
- What is correct: the non-slow list/tree behavior appears to be in place. The same focused run passed the other scenarios covering plain-lib exclusion, `:required-by` in `--edn`, diamond provenance, and the human table REQUIRED BY column. The missing piece is the actual transitive activation of contributions on launcher startup.



## Fix (work-2, verification feedback)

HEAD: isaac-foundation `dceb2af`

merge-resolved-classpath-modules double-unwrapped implied entries (`(get entry id)`
on an already-unwrapped value), leaving transitive modules as nil in the index.
Launcher greet now works; `env ISAAC_GIT=1 bb features-all features/module/
module_deps.feature features/module/modules_list.feature` → 10/10 green.

## Verification notes

- Verification passed on 2026-06-18 against fetched GitHub `isaac-foundation` `main` at `dceb2af`, not the stale local `../plan/isaac-foundation` mirror.
- Focused proof: `env ISAAC_GIT=1 bb features-all features/module/module_deps.feature features/module/modules_list.feature` in `isaac-foundation` → `10 examples, 0 failures, 24 assertions`.
- The launcher-path regression is fixed: the transitive module contribution now activates, while the previously-green list/tree scenarios remain green.
