---
# isaac-vwa6
title: 'Handbook chapter: isaac-acp'
status: completed
type: task
priority: normal
created_at: 2026-09-30T04:56:36Z
updated_at: 2026-10-05T14:38:25Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-acp` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

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

main-sha: isaac-acp e257230

Shipped `src/isaac/comm/acp/handbook.md` (manifest `:handbook`), a lint
spec (`spec/isaac/comm/acp/handbook_chapter_spec.clj`, following
isaac-gmail's isaac-g8k2 raw-manifest pattern) checking every backtick
`config:<path>` ref against the composed schema and every `isaac
<command>` invocation against the registered CLI. isaac-acp declares no
config schema of its own, so no `:description` backfills were needed.

Also added `:builtin? true` to the manifest (matching isaac-agent's
isaac-aaf4 and isaac-gmail's isaac-g8k2), needed for this module's own
"acp" command and `:handbook` resource to resolve via
`discovery/builtin-index` in its own tests. No pins bumped. `bb ci`
green locally (81 spec examples, 70 feature examples) and on GitHub CI
(run 36676883608). The `:handbook` key warns as unknown under the
pinned foundation sha in this repo's own tests, as expected until a
foundation repin.

Chapter covers: the ACP comm and initialize/agentInfo, session
selection and per-turn overrides (shared frequencies flags plus the
blank-invocation defaults.frequencies fallback, isaac-asik), episodes
rotating beneath a fixed sessionId, streaming/replay notification
shapes, tool-call display lifecycle, session/cancel, slash command
advertisement and /status, and errors/exceptions/compaction status
surfacing as agent_message_chunk/agent_thought_chunk with end_turn.

`[verify]` in the chapter: whether ACP's `promptCapabilities.text`-only
advertisement (no image/audio) is a permanent design choice or just
not-yet-implemented.

main-sha: isaac-acp 3f9be4d (builtin flag reverted)

Follow-up: `:builtin? true` changed isaac-acp's runtime loading (eager
classpath activation per isaac-foundation's `eager-load?` /
`classpath-builtin-index`), which a docs bean must not do. Removed the
flag; `handbook_chapter_spec.clj` now reads isaac-acp's own manifest
directly via `isaac.module.discovery/manifest-resource` (not
builtin-index) and unions its `:isaac/cli` commands with
foundation/agent's builtin commands for the CLI-mention check. `bb ci`
green locally and on GitHub CI (run 36677650624).
