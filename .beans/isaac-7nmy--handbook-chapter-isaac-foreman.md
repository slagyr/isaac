---
# isaac-7nmy
title: 'Handbook chapter: isaac-foreman'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-09-30T04:56:36Z
updated_at: 2026-09-30T06:33:05Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-foreman` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

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

main-sha: isaac-foreman c0836e3

Handbook chapter at isaac-foreman's src/isaac/foreman/handbook.md (manifest
:handbook isaac/foreman/handbook.md), plus a lint spec at
spec/isaac/foreman/handbook_chapter_spec.clj using discovery/builtin-index
directly (foreman's manifest already carries :builtin? true, unchanged —
no isaac-imessage-style raw-manifest workaround needed). Backfilled schema
:description on :resource-pools and :prompt (both action-shape copies:
the shared :foreman :actions pool and machine-local :actions), and on
transition-row :start/:event/:end. No pin bump; isaac-foundation
3a199d92c4 / isaac-agent 6736ca27ff / isaac-http 8e01658366 unchanged.
`bb ci` green locally (76 spec examples, 23 feature scenarios) and on
GitHub CI (run 36678626021).

Config-path note: this pinned foundation's `schema.resolve` recognizes
entity collections via a hardcoded set (`:berths :gauges :foundries :crew
:hail :models :providers`) rather than the newer structural check —
`machines` isn't in that set, so a bare `machines.lighthouse-watch.field`
path does not resolve. Every config: ref in the chapter therefore uses
bracket-string syntax (`machines["lighthouse-watch"].field`,
`foreman.actions["tend-lamp"].field`), which resolves generically for any
:map-typed schema node regardless of that set — confirmed by testing
every ref in the chapter directly against schema-resolve/schema-for-data-path
before writing it in.

[verify] items in the chapter text: the `:notify` action type is declared
in the config schema (`:one-of? :log :turn :notify`) but nothing in
core.clj/checks.clj ever executes or resolves a pending `:notify` entry —
documented as a known, currently-inert gap rather than a bug, flagged
`[verify]` for Micah to confirm that's the intended state (vs. a real gap
to bean separately).
