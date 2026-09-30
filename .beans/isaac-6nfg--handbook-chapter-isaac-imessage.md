---
# isaac-6nfg
title: 'Handbook chapter: isaac-imessage'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-09-30T04:56:36Z
updated_at: 2026-09-30T06:14:30Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-imessage` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

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

main-sha: isaac-imessage 8bda002

Handbook chapter at isaac-imessage's src/isaac/comm/imessage/handbook.md
(manifest :handbook isaac/comm/imessage/handbook.md), plus a raw-manifest
lint spec at spec/isaac/comm/imessage/handbook_chapter_spec.clj (follows
isaac-gchat's pattern: merges this module's own isaac-manifest.edn into
discovery/builtin-index rather than flagging :builtin? true, since that
flag also affects eager module loading — out of scope for this doc-only
bean). No pin bump; isaac-foundation 9ab2527 / isaac-agent b6eb475 /
isaac-http 689d368 unchanged. Full CI green on main (run 36677045942).

Note: comms.imessage.imessage/<field> config paths do not resolve via
isaac.config.schema.resolve at this pinned foundation sha (comms isn't in
schema-resolve's entity-collections set) — confirmed by testing directly,
which is also presumably why isaac-gchat's own landed chapter carries zero
real backtick config: refs. The chapter therefore documents fields via
their bare names plus fenced `config set`/`config unset` examples (both
tested against the module's real manifest fields) rather than backtick
config: refs, and the lint spec's config:-ref check is real but exercises
zero references today — same as isaac-gchat's.

[verify] items in the chapter text itself: the exact macOS Automation
permission name/dialog for imsg's send path (Full Disk Access is directly
grounded in code/specs; Automation is inferred, not asserted anywhere in
this repo), and whether the total lack of attachment support is an
intentional MVP gap or a wanted follow-up bean.
