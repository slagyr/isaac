---
# isaac-0xp9
title: 'Handbook chapter: isaac-discord'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-09-30T04:56:36Z
updated_at: 2026-09-30T06:15:38Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-discord` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

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

main-sha: isaac-discord feea68768d8172e56989076d2a0cabe83373b30f

CI: green (isaac-discord CI Tests, run 36677102734).

Chapter at `isaac-discord/src/isaac/comm/discord/handbook.md` (manifest `:handbook`), lint spec at `isaac-discord/spec/isaac/comm/discord/handbook_chapter_spec.clj` (raw-manifest approach, mirrors isaac-google/isaac-cron since isaac-discord is not `:builtin?` and carries an older foundation pin). [verify] items left in the chapter text for Micah's review: the exact clip-vs-third-message split behavior, the `comms.discord.model` field being schema-undeclared, and the Discord close-code 4004/>=4010 fatal-close interpretation.
