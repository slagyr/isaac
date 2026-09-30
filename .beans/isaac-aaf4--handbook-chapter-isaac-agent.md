---
# isaac-aaf4
title: 'Handbook chapter: isaac-agent'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-09-30T04:56:35Z
updated_at: 2026-09-30T05:21:36Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-agent` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

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

Chapter at `src/isaac/agent/handbook.md` (manifest `:handbook "isaac/agent/handbook.md"`), covering: Crews, Tools and directories, Sessions and transcripts, Turns and the tool loop, Compaction and context modes, Providers/models/effort, Bridge and slash commands, Comms and delivery — each with a `### Troubleshooting` subsection. Lint spec at `spec/isaac/agent/handbook_chapter_spec.clj` (mirrors foundation's; passes — 3 examples, 0 failures). Backfilled missing schema `:description`s: `resource-pools` (table + all 5 fields), `tools.web_search.provider`/`.api-key`, the three `:compaction` maps (`:async?`, `:head`, `:strategy`, `:threshold`, plus the crew-level map's own top description), `attention.break-glass.comm`/`.target`, and 8 retired fields (`tools.max-parallel`/`.defaults`/`.allow`/`.deny`/`.directories`, `crew.max-in-flight`, and 8 `:defaults` top-level retired fields). One `[verify]` flag in the chapter: `attention.break-glass` is declared but confirmed unused anywhere in source/features — flagged for Micah rather than asserted as functional.

`bb ci` green locally (1834 spec examples + 912 feature examples, 0 failures). GitHub CI green on agent main.

main-sha: isaac-agent 7509c43d48f5d7ff8be302d915938b3820cd4832
