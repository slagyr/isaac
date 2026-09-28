---
# isaac-5gu1
title: Remove :reach (fan-out) from session frequencies everywhere
status: draft
type: feature
priority: normal
created_at: 2026-09-28T01:32:07Z
updated_at: 2026-09-28T01:32:07Z
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
