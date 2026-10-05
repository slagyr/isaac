---
# isaac-erjv
title: 'Handbook chapter: isaac-cli-server'
status: completed
type: task
priority: normal
created_at: 2026-09-30T04:56:36Z
updated_at: 2026-10-05T14:38:34Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-cli-server` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

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

## Landed on main (2026-09-30)

main-sha: isaac-cli-server 54285968e673a6ae44276d1c951d53e3f8ae29a9

`bb ci` green locally (20 spec examples incl. the 4 new handbook-lint
examples, 20 feature examples) and on GitHub CI (run 36678730843).

Also bumped isaac-cli-server's isaac-foundation pin to `0c6e881` and
isaac-http pin to `42e302f` (both origin/main tips as of this bean;
deps.edn + bb.edn) — no fallout from the known upstream changes (this
module doesn't depend on isaac-agent, so isaac-e9jl's async
`worker/tick!` doesn't apply here; isaac-q1iu's bearer adjudication
didn't touch anything this module's tests exercise).

Handbook chapter: `src/isaac/cli_server/handbook.md` (topic
`isaac.cli-server`), covering the `/cli` endpoint, the isaac-jvzn
per-command scope model, local-only/`--root` refusal, the grace window,
and stale-basis refusal, each with `### Troubleshooting`. One `[verify]`
left for Micah: whether the fixed 2s grace window should become a config
key. Lint spec: `spec/isaac/cli_server/handbook_chapter_spec.clj`
(isaac-imessage's non-`:builtin?` pattern — merges this module's own raw
manifest into `discovery/builtin-index` for schema composition, since
this bean doesn't add `:builtin? true`).
