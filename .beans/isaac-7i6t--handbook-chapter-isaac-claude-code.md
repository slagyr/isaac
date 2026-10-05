---
# isaac-7i6t
title: 'Handbook chapter: isaac-claude-code'
status: completed
type: task
priority: normal
created_at: 2026-09-30T04:56:36Z
updated_at: 2026-10-05T14:38:33Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-claude-code` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

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

## Landed on main

main-sha: isaac-claude-code d06922368009fb0c264da82ad0f0dd455a7f12a5

Pins bumped (deps.edn + bb.edn) to isaac-foundation `0c6e881e`, isaac-agent
`13d18c70`, isaac-http `42e302f3` — required for the manifest's `:handbook`
key to be recognized (the previously pinned foundation predated `:handbook`
support and warned `:manifest/unknown-key`). `bb ci` green against the
bumped pins with no other fallout; GitHub CI green
(https://github.com/slagyr/isaac-claude-code/actions/runs/36678750887).

Chapter: `src/isaac/llm/handbook.md` (manifest `:handbook
"isaac/llm/handbook.md"`). Lint spec:
`spec/isaac/llm/handbook_chapter_spec.clj` — since this module's own
manifest carries no `:builtin? true`, the spec finds its own manifest by id
(`isaac.module.discovery/manifest-resource`) and folds it into the builtin
index rather than declaring the module builtin just to pass.

`[verify]` items left in the chapter text for Micah: the exact `claude` CLI
login/re-login subcommand name (auth is `auth: "none"` — Isaac never
manages this provider's credential, so the exact external command wasn't
grounded in this repo's code).
