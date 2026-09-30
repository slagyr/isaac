---
# isaac-8m6y
title: 'Handbook chapter: isaac-google'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-09-30T04:56:36Z
updated_at: 2026-09-30T05:41:01Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-google` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

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

## Worker notes

Shipped `src/isaac/google/handbook.md` (~3500 words) plus
`spec/isaac/google/handbook_chapter_spec.clj`, manifest gained `:handbook`
and a version bump (0.1.15 → 0.1.16). All schema fields already carried a
`:description`; none needed adding.

Pins: **not bumped**. Followed isaac-cron's fallback (isaac-lp5y) instead —
the lint spec reads raw manifests for `known-cli-commands` rather than
`isaac.module.berths/module-report`, which this module's current
foundation pin (9ab25271…, same as isaac-cron's) doesn't need for this
pattern. `bb ci` is green at the existing pins (isaac-foundation
9ab25271…, isaac-agent b6eb475c…); no isaac-agent/isaac-http bump was
needed for this bean.

One `[verify]`-worthy note left inline in the chapter itself is absent —
every claim traced to source/manifest/features directly, so no `[verify]`
markers were needed in the final text.

main-sha: isaac-google cd43d0c845a402c4b779d3a7aca1ead9c7100a93
