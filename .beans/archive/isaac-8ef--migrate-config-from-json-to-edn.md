---
# isaac-8ef
title: "Migrate config from JSON to EDN"
status: completed
type: task
priority: normal
created_at: 2026-04-16T16:21:28Z
updated_at: 2026-04-16T16:27:29Z
---

## Description

Isaac config is currently JSON (isaac.json). Since we're not a drop-in for OpenClaw, switch to EDN (isaac.edn). EDN is native to Clojure, supports keywords and sets, and doesn't need cheshire for parsing.

## Scope
- src/isaac/config/resolution.clj — change read-json-file to read EDN, update load-config paths from .json to .edn
- spec/isaac/config/resolution_spec.clj — update write-json! helper to write EDN, update all test config setup
- features/cli/acp.feature — references isaac.json
- ~/.isaac/isaac.json → ~/.isaac/isaac.edn (user config migration)
- Env variable substitution (${VAR}) must still work in EDN string values
- Remove openclaw.json fallback chain (no longer needed)
- Remove cheshire dependency from config/resolution.clj

## Acceptance criteria
- Config loads from ~/.isaac/isaac.edn
- Env substitution works in EDN string values
- All config resolution specs pass with EDN
- Features pass
- JSON config no longer loaded

