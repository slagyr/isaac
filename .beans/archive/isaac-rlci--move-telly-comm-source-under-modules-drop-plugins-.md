---
# isaac-rlci
title: "Move telly comm source under modules/; drop plugins/ directory"
status: completed
type: task
priority: normal
created_at: 2026-05-05T18:51:35Z
updated_at: 2026-05-05T19:14:31Z
---

## Description

Why: Telly is half-migrated. Its module.edn manifest lives at modules/isaac.comm.telly/module.edn, but its source still lives at plugins/isaac/comm/telly.clj — and bb.edn :paths includes the legacy plugins/ directory to keep the namespace loadable. Discord migrated cleanly to modules/isaac.comm.discord/; telly should match.

## Scope

- git mv plugins/isaac/comm/telly.clj -> modules/isaac.comm.telly/src/isaac/comm/telly.clj
- bb.edn :paths: drop "plugins"; add "modules/isaac.comm.telly/src"
- Remove now-empty plugins/ directory entirely (and any references in .gitignore, deps.edn, etc.)
- Run bb spec and bb features to confirm nothing broke

## Acceptance

- plugins/ directory no longer exists
- bb.edn :paths reflects the modules/ layout for both telly and discord
- bb spec passes
- bb features passes (no regressions in features/lifecycle/reconciler.feature, features/modules/*.feature, etc.)

## Acceptance Criteria

plugins/ removed; telly source under modules/isaac.comm.telly/src; bb.edn :paths updated; bb spec and bb features pass

