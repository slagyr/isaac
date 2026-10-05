---
# isaac-0l17
title: 'Handbook chapter: isaac-hooks'
status: completed
type: task
priority: normal
created_at: 2026-09-30T04:56:36Z
updated_at: 2026-10-05T14:38:33Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-hooks` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

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

main-sha: isaac-hooks 92394bc

Shipped `resources/isaac/hooks/handbook.md` (manifest `:handbook`), a lint
spec (`spec/isaac/hooks/handbook_chapter_spec.clj`, following
isaac-episodes' pattern) checking every backtick `config:<path>` ref
against the composed schema and every `isaac <command>` against the
registered CLI, and `:description`s for the two undescribed schema
fields (the `isaac.hooks/hook` berth's `:factory`, the retired
`hooks.auth.token`).

Bumped isaac-foundation/isaac-agent/isaac-http pins (deps.edn + bb.edn,
every occurrence) to current `origin/main` tips — the manifest's
`:handbook` key needs a foundation new enough to read it (isaac-ppyj
renamed `:manual` to `:handbook`). No code fallout from the bump: `bb ci`
green locally (config-bypass-lint, 36 spec examples, 20 feature
examples) and on GitHub CI (run 36673933216).

`[verify]` in the chapter: whether any builtin module currently
contributes a module-sourced hook via the `isaac.hooks/hook` berth
(none does today), and the exact failure surfaced when a contributed
hook's `:factory` can't be resolved.
