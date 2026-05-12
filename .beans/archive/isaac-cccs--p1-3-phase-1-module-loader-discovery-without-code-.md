---
# isaac-cccs
title: "P1.3 Phase 1 module loader: discovery without code load"
status: completed
type: task
priority: normal
created_at: 2026-04-30T22:36:14Z
updated_at: 2026-05-05T01:09:43Z
---

## Description

Read :modules from config, resolve coordinates, parse manifests,
build an in-memory index. NO code is loaded.

## Scope

- isaac.module.loader/discover!
  - Inputs: config (with :modules list) + state-dir
  - For each id in :modules:
    - resolve via isaac.module.coords
    - read manifest via isaac.module.manifest
  - Builds index: {module-id manifest}
  - Topologically sorts by :requires for reporting (not yet for load,
    since loading is Phase 2)
- Hard error on:
  - unresolved coordinate
  - invalid manifest
  - duplicate :id
  - cycle in :requires

## Why hard error

Misconfiguration should surface at startup, not at first chat.

## Acceptance

- discover! returns the index; no module source files have been read
- Tests cover: missing module, malformed manifest, duplicate id,
  requires-cycle
- Time to discover N modules is O(N) file reads — measured

## Doesn't include

- Phase 2 activation (separate epic)
- Schema composition (P2.x)
- Wiring into config-loader (P1.4)

## Notes

Verification failed: bb spec passes and bb features features/modules/discovery.feature is now green. Code review also supports the 'no module source code is loaded' behavior in src/isaac/module/loader.clj, which only resolves module directories and slurps module.edn. However, the bead's explicit acceptance still says 'Time to discover N modules is O(N) file reads — measured', and I could not find any measurement/spec/documented proof of that in src/, spec/, or features/. I also did not find an explicit test/instrumentation proving discover! avoids reading module source files beyond code inspection. Acceptance is therefore still incomplete.

