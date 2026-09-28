---
# isaac-5gu1
title: Remove :reach (fan-out) from session frequencies everywhere
status: todo
type: feature
priority: normal
created_at: 2026-09-28T01:32:07Z
updated_at: 2026-09-28T01:58:27Z
parent: isaac-q3u3
---

Likely repos: **isaac-agent** first (schema + resolver), then every consumer: isaac-hail, isaac-gchat, isaac-cron, isaac-hooks, isaac-discord, isaac-acp; plus deployed config in **orchestration** and live config on zanebot/yopp.

## Decision (2026-09-27, Micah)

Kill fan-out. `:reach :all` has no deployed use and no foreseeable one; it breaks the one-turn-one-response model, and anyone who wants several turns can submit several. With `:all` gone, `:one` is the only mode, so the **`:reach` key is removed entirely** — clean cutover: `:reach` in any frequencies map (config or runtime) hard-rejects as an unknown key. No alias, no silent ignore.

Background: Hail's router fans a band out to every matching session; gchat's `session-keys` (`handler.clj`) fans a space message out itself using Agent's `matching-sessions`. Both are clients selecting sessions before admission, which isaac-l3vb moves into Agent anyway.

## Scope (files using `:reach` on main, 2026-09-27)

- isaac-agent: `session/frequencies.clj` (`reach-modes`, schema, docstring), `session/frequencies_cli.clj`, `config/defaults.clj`, `resources/isaac-manifest.edn`, `features/bridge/cli-prompt.feature`
- isaac-hail: `hail/router.clj` (broadcast path, `:children`, `broadcasts/`), `hail/bands.clj`, `hail/cli.clj`, `hail/http.clj`, `tool/hail.clj`, `isaac-manifest.edn`; features band-inheritance, bands, hail-band-data, router, session-create
- isaac-gchat: `handler.clj` `session-keys` fan-out branch, manifest
- isaac-cron: `cron/service.clj`, manifest
- isaac-hooks: `hooks.clj`, manifest
- isaac-discord: `comm/discord.clj`, manifest
- isaac-acp: `features/comm/acp/default_frequencies.feature`
- orchestration: `isaac-beans/config/hail/_orchestration-template.edn`, `isaac-ci/config/hail/ci-failure.md` (both `:reach :one` — delete the line)
- Live config on zanebot and yopp: strip `:reach` from hail bands, crews, spaces **before** deploying the Agent that rejects it.

## Scenario plan (to draft)

1. A frequencies map with `:reach` fails config validation, naming the key (Agent).
2. A Hail band with `:reach` fails config validation (Hail).
3. A gchat space entry with `:reach` fails config validation (gchat).

Removal checks (no broadcast code path left, `git grep ':reach'` empty per repo) are one-time acceptance items, not scenarios.


## Live migration (2026-09-27, Micah: migrate zanebot and yopp)

Every live use is `:reach :one`, which is already the default (Hail `bands.clj` and `router.clj` fill `:one` when absent). So stripping the key is a no-op today and can happen **before** this bean ships — do it first, then the Agent that rejects `:reach` deploys with nothing to trip on. Inventory as of 09-27: zanebot has six live hail band/template files carrying it (plus `.bak` copies — leave or delete, they aren't loaded); yopp has one hail band. The orchestration repo's two files are the source of the zanebot templates.


- [x] 2026-09-27: live `:reach :one` stripped on zanebot and yopp (`isaac config validate` OK on both) and in orchestration (df94dbd). Nothing live carries `:reach` now.


## Fan-out scenarios owned here (2026-09-27)

isaac-hail scenarios that exist only for fan-out are removed by this bean (not by isaac-ex4q): `bands.feature:34` (becomes: `:reach` is rejected as an unknown key), `delivery.feature:222`, `explicit-session-routing.feature:37`, `hail-get.feature:67` and `:79`, `hail-naming.feature:40`, `router.feature:142` and `:313`. Do not edit any `@wip` scenario another bean has baselined.


## Acceptance

Scenarios (`@wip` on main): isaac-agent 928ff78, isaac-hail f08d6b5. Remove `@wip`; all pass:

- [ ] isaac-agent: `bb features features/session/default_frequencies.feature:79` — `:reach` in `:defaults :frequencies` fails `config validate`
- [ ] isaac-hail: `bb features features/bands.feature:34` — a band with `:reach` fails validation; `bb features features/send-addressing.feature:82` — `hail send --prompt 'orphan'` (no selector, no `--reach` flag) still refuses naming addressing
- [ ] Already done on main by the planner (f08d6b5): fan-out scenarios deleted (delivery reach-all child, explicit-session prevents fan-out, hail-get broadcast parent + fan-out child, hail-naming reach-all children, router broadcast + reach-all zero-match); `reach :one` rows/lines stripped from all Hail scenarios (it was the default); bands frontmatter type-conflict scenario now uses `prefer: 5`.
- [ ] Unit specs: gchat space schema rejects `:reach`; cron, hooks, discord manifests no longer declare it.
- [ ] One-time checks: `git grep -n ':reach\|reach-modes\|--reach'` finds nothing in src/resources of isaac-agent, isaac-hail, isaac-gchat, isaac-cron, isaac-hooks, isaac-discord. Hail's broadcast code (`hail/broadcasts`, `:children`, `:source-hail`) is gone.
- [ ] Do not edit isaac-acp `features/comm/acp/default_frequencies.feature` — its `:reach :one` prose line belongs to isaac-asik's baselined contract; fix it after asik lands.
- [ ] Each module: `bb verify` green, version bump, repin isaac-agent; bump the modules.edn registry.

feature-baseline: isaac-agent 928ff788bcb444b8e170fdeeafb08485a0895e3d
feature-baseline: isaac-hail f08d6b500ec2b86d3b0c00e29e6ec0e48b2068fb
feature-blob: isaac-agent features/session/default_frequencies.feature db5289eb885b00b066fe720a08b3b23a2193a705 79
feature-blob: isaac-hail features/bands.feature 1c9105533a86daebef51e6d05ed3dd4af37ad861 34
feature-blob: isaac-hail features/send-addressing.feature bb1721453d51ce5d4412b7e320afee17b53dbf76 82
