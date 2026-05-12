---
# isaac-pk8u
title: "Collapse body-bearing config entities to single markdown with EDN frontmatter"
status: completed
type: task
priority: low
created_at: 2026-04-27T18:20:44Z
updated_at: 2026-04-27T21:12:06Z
---

## Description

Today, entities that have a textual body (crew soul, cron prompt) live as two files: <name>.edn for config + <name>.md for the body. Hooks (planned next) follow the same pattern.

Consolidate to a single .md per entity with EDN frontmatter:

  ---
  {:model :grover
   :tools {:allow [:read :write]}}
  ---

  You are Isaac.
  Speak like a tired librarian.

Scope: entities that HAVE a body — crew, cron, hooks. Models and providers stay as .edn (no body to put below the fence).

Format choice rationale:
- --- fences match YAML's visual convention, no parser surprise.
- EDN content inside fences keeps Isaac Clojure-first; no YAML dep, no quoting friction.

Migration:
- config/loader.clj reads both shapes during a deprecation window: <name>.md (preferred) and <name>.edn + <name>.md (legacy). Single-file shape wins when both exist.
- Existing crew + cron entities convert to single .md.
- Hooks (when implemented) ship in the new shape from day one.

Out of scope:
- Models, providers, root isaac.edn. They're pure config, no body.

Acceptance:
1. Loader recognizes <name>.md with frontmatter for crew, cron, hooks.
2. Existing two-file shape still works during migration; warn (not error) on dual-format collision.
3. Migration script or manual pass converts in-tree examples (zanebot configs, isaac init scaffold).
4. Schema validation runs against the parsed frontmatter same as today.
5. bb features and bb spec pass.

## Notes

Verification gap fixed: loader and schema now recognize hooks as body-bearing configs in both single-file markdown-frontmatter and legacy edn+md shapes. Updated features/server/hooks.feature to the new single-file hook shape. bb spec and bb features both pass.

