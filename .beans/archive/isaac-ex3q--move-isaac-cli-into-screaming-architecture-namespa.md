---
# isaac-ex3q
title: "Move isaac.cli.* into screaming-architecture namespaces"
status: completed
type: task
priority: low
created_at: 2026-05-06T14:43:56Z
updated_at: 2026-05-06T15:12:37Z
---

## Description

Why: 'cli' is a framework concern, not a domain. Per Uncle Bob's screaming architecture (see isaac-cqh), top-level structure should reflect what Isaac IS, not how it's invoked. Migrate the 10 isaac.cli.* namespaces into their domain homes; keep isaac.cli only for the command-dispatch infrastructure.

## Mapping

- isaac.cli.registry + isaac.cli.init -> merge into a single isaac.cli (top-level ns: command registration table + first-run bootstrap)
- isaac.cli.acp                          -> isaac.acp.cli
- isaac.cli.server                       -> isaac.server.cli
- isaac.cli.auth                         -> isaac.auth.cli
- isaac.cli.crew                         -> isaac.crew.cli
- isaac.cli.sessions                     -> isaac.session.cli
- isaac.cli.chat + isaac.cli.chat.toad   -> merge into isaac.bridge.chat-cli
- isaac.cli.prompt                       -> isaac.bridge.prompt-cli

After the move, the isaac.cli/ directory is gone; only the top-level isaac.cli namespace remains.

## Why these groupings

- '<domain>.cli' suffix keeps the CLI surface visibly separate from the domain's pure logic (same pattern isaac.api.* uses for module-public surfaces).
- 'isaac.bridge.*' captures user-facing interactive surfaces (chat-cli, prompt-cli). Bridge is the spaceship metaphor for the user-facing surface; chat and toad are two skins on the same interactive bridge.
- isaac.cli stays as the dispatch+bootstrap infrastructure ns. It is genuinely framework code, not a domain concern.

## Scope

- Move the 10 source files to their new locations and update the (ns ...) form.
- Update every :require referencing the old namespaces (in src/, spec/, features/, modules/).
- Move corresponding spec files in spec/isaac/cli/ to mirror the new layout.
- Update bb.edn / deps.edn :paths if any reference cli paths (probably not, since they're under src/).
- Verify all features and specs still pass.

## Out of scope

- Other screaming-architecture moves (server.* -> bridge.*, llm.* -> drive.*, etc.). Those are isaac-cqh's broader work; this bead is scoped to the cli subtree only.
- Renaming chat-cli/prompt-cli to something else mid-migration. Keep the names settled in this scope.

## Acceptance

- src/isaac/cli/ directory no longer exists; only top-level src/isaac/cli.clj remains (dispatch + bootstrap).
- All 10 sources moved per the mapping above.
- spec/isaac/cli/ likewise restructured to match.
- bb spec passes.
- bb features passes.
- 'grep -rn isaac.cli.acp' (etc.) returns no stale references.

## Acceptance Criteria

src/isaac/cli/ directory removed; 10 namespaces migrated per the mapping; specs/features pass; no stale isaac.cli.<old> references remain

