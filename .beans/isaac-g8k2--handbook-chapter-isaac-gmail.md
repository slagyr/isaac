---
# isaac-g8k2
title: 'Handbook chapter: isaac-gmail'
status: completed
type: task
priority: normal
created_at: 2026-09-30T04:56:36Z
updated_at: 2026-10-05T14:38:34Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-gmail` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

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

main-sha: isaac-gmail d743846

Added `:handbook "isaac/comm/gmail/handbook.md"` to the manifest,
`src/isaac/comm/gmail/handbook.md` (~2860 words), and
`spec/isaac/comm/gmail/handbook_chapter_spec.clj` (raw-manifest lint,
mirroring isaac-google's/isaac-cron's pattern since `isaac.module.berths`
varies across pinned foundation shas). No pin bumps — landed at gmail's
existing isaac-foundation/isaac-agent/isaac-http/isaac-google pins.
`bb ci` green locally and on GitHub CI (run 36675694940).

All config keys the manifest declares already carried `:description`s —
no schema changes needed.

`[verify]` items left in the chapter text for Micah:
- `gmail/allow-from`'s retirement note (chapter says it shows up nowhere
  in `config get`/`config validate` output — inferred from its `:type
  :ignore` + `:retired?` validation, not directly observed).
- Whether an attachment is only saved on `:converse` routes (inferred from
  `start-turn!` being the only caller of `inbound-attachment/save-all!`).
- Whether `gmail/pull`'s scheduler task is actually cancelled/rescheduled
  on a hot config reload vs. only at process (re)start (inferred from
  `module.clj`'s `on-load`/`on-unload`, not from a feature scenario).

Also worth Micah's attention, not a chapter defect: at gmail's current
isaac-foundation pin (9ab2527), `isaac.module.manifest/read-manifest`
does not yet list `:handbook` in its `known-keys`, so discovery logs
`:manifest/unknown-key :handbook` and the module-index's conformed
manifest drops the field (namespaced/berth keys survive this; `:handbook`
does not). isaac-google and isaac-cron ship `:handbook` at the same
foundation pin, so this is pre-existing and not introduced here — but if
`isaac.handbook`'s chapter lookup ever reads through that same
conform/index path (rather than a raw manifest slurp), gmail's (and
google's/cron's) chapters could be invisible to `handbook__read` on a
host still pinned this old. Not investigated further — out of scope for
this bean.
