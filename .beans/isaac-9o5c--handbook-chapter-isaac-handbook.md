---
# isaac-9o5c
title: 'Handbook chapter: isaac-handbook'
status: completed
type: task
priority: normal
created_at: 2026-09-30T04:56:37Z
updated_at: 2026-10-05T14:38:34Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-handbook` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

- Cover the concepts THIS module owns (its config tables and keys, tools, comms, commands, behaviors), written for a MODEL operating Isaac, not a developer: what it is, how to change it with `handbook__configure` (config paths), how to verify, then a `### Troubleshooting` subsection under each concept. No source-code walkthroughs.
- Ground every claim in the module's code, manifest and feature scenarios. Mark anything you can't verify with `[verify]` for Micah.
- Refer to other modules' concepts by one line plus their topic id (e.g. crews → `isaac.agent`); don't re-document them. Use foundation's chapter (`isaac-foundation/src/isaac/foundation/handbook.md`) as the pattern and vocabulary.
- Schema `:description`s: any config key this module declares without a `:description` gets one (the config reference is generated from them).

## Acceptance

- Manifest `:handbook` names the chapter; loading config raises no "handbook ... not found" warning.
- A lint spec like foundation's `spec/isaac/foundation/handbook_chapter_spec.clj` (backticked `config:<path>` refs resolve in the composed schema; `isaac <command>` refs exist).
- The repo's full CI green; landed on main with a single squash commit.

## Ungated

Documentation plus a lint spec; no behavior change. Worker hands off `tag=unverified`; Micah reviews the chapter text.



## Worker notes (isaac-9o5c)

Chapter rewritten to the foundation/agent/mcp standard: `## Reading the
handbook`, `## Changing config`, `## Granting the tools`, `## Shipping a
chapter`, each with `### Troubleshooting`. Flags handbook__read reference
topics (module:/crew:/config: ids) as drafted but not yet built (isaac-niqx,
`features/reference.feature` still `@wip`) — `[verify]` once that lands.
`handbook.max-chars` already had a schema `:description` (isaac-lshz).
Added `spec/isaac/handbook/handbook_chapter_spec.clj`, mirroring
foundation/agent/mcp's raw-manifest lint pattern. `bb ci` green locally
(pinned deps) and on GitHub CI.

main-sha: a0cc953
