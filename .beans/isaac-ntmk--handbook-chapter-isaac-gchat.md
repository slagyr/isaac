---
# isaac-ntmk
title: 'Handbook chapter: isaac-gchat'
status: completed
type: task
priority: normal
created_at: 2026-09-30T04:56:36Z
updated_at: 2026-10-05T14:38:34Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-gchat` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

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

main-sha: isaac-gchat 5c2a09aa6c75b3666f49c1a83cc45648f16ee807

No pin bump. Bumping isaac-foundation/isaac-agent/isaac-http to their
origin/main tips reproducibly broke the isaac-xoqn feature scenario
("three quick messages in one DM thread get one consolidated reply") —
fails only with the bumped isaac-agent pin (queue/tick changes landed on
isaac-agent main since), passes clean on the current pin. Reverted the
bump and followed isaac-cron's precedent instead: `:handbook` added
against the current foundation pin, which logs a `:manifest/unknown-key`
warning for `:handbook` (that foundation predates handbook support)
rather than a "not found" warning. `bb ci` is green on the current pins
(200 spec + 60 feature examples); GitHub CI on main confirms it.
