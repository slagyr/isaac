---
# isaac-0yp1
title: 'Module deps via deps.edn: auto-load transitive modules + list-as-tree (REQUIRED BY)'
status: draft
type: feature
priority: normal
created_at: 2026-06-18T19:21:29Z
updated_at: 2026-06-18T20:00:47Z
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
