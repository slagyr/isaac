---
# isaac-lp5y
title: 'Handbook chapter: isaac-cron'
status: completed
type: task
priority: normal
created_at: 2026-09-30T04:56:36Z
updated_at: 2026-10-05T14:38:34Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-cron` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

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

main-sha: isaac-cron 72781c4

Ships `src/isaac/cron/handbook.md` (topic `isaac.cron`): Cron jobs (the
`:cron` table and its fields), Prompt content (inline vs. companion
markdown, the non-exclusive `:config/companion-inline-wins` behavior),
Scheduling and timezone, Session targeting, Delivering results, and Job
state (`<root>/cron.edn`) — each with How to change / How to verify /
Troubleshooting. Cross-refs `isaac.foundation` (scheduler mechanism, vocab)
and `isaac.agent` (crew, session-frequencies, comm/delivery, sessions CLI)
by one line instead of re-documenting them.

Every `:cron` schema field already carried a `:description` — no schema
edits needed beyond adding the manifest `:handbook` key and bumping
`:version` 0.1.6 → 0.1.7.

Lint spec `spec/isaac/cron/handbook_chapter_spec.clj` mirrors foundation's,
but reads each module's raw manifest `:isaac/cli` map directly instead of
`isaac.module.berths/module-report`, which doesn't exist at isaac-cron's
current foundation pin (`9ab25271a`). At that same pin, `config:` path
resolution for a dynamic entity table (`:cron.<job>.<field>`) isn't
generalized yet (only a hardcoded set of table names is), so the chapter
uses the `config:cron.<name>.<field>` **placeholder** form (skipped by the
lint) for that pattern and reserves a real backtick `config:tz` reference
for the one static key. I tried bumping the foundation pin to origin/main
to get both fixed for real; it then failed schema composition on `:crew`
(`missing lex :model-exists?`) because isaac-cron's pinned isaac-agent
doesn't yet register that lexicon entry either — a matching agent bump
would be needed too. Reverted; left pins unchanged as the lower-risk choice
for a docs-only bean. A future pin bump (foundation + agent together) will
let this lint spec go back to using `isaac.module.berths` and let the
chapter reference real per-job `config:` paths.

`[verify]` items in the chapter for Micah:
- whether a crew running inside Isaac can read `<root>/cron.edn` directly
  (state, outside `config/`) — filesystem-boundary rules weren't fully clear
  from the code I read.
- the recipient format of `to` (comm-specific — Discord channel id, iMessage
  handle, etc.) since cron itself doesn't validate or document it.
- the scheduler's poll interval (~30s, from `isaac.cron.service`'s
  `default-tick-ms`) — flagged as module-internal, not a documented
  contract.

CI: green (`bb ci` locally — 28 spec examples / 35 assertions, 21 feature
examples / 36 assertions — and GitHub Actions run 36673123945 on
isaac-cron, `verify` job, all green).
