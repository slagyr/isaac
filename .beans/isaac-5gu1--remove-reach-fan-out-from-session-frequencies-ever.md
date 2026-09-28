---
# isaac-5gu1
title: Remove :reach (fan-out) from session frequencies everywhere
status: completed
type: feature
priority: normal
created_at: 2026-09-28T01:32:07Z
updated_at: 2026-09-28T16:08:32Z
parent: isaac-q3u3
blocked_by:
    - isaac-asik
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


## Planner adjustment (2026-09-28, prowl@isaac-plan) — type-conflict uses create

Do not restore `prefer: 5`. `prefer` is no longer a schema-checked band field, so that fixture exits 0. The scenario must fail validation. `create: 5` does. That is the live contract, already on isaac-hail main `c8fbb23` (worker CI fix). The gate was still holding the pre-fix blob.

### Re-baselined

    feature-baseline: isaac-hail c8fbb233919ec6ed2bd5098cbcdabf6bd379fec0
    feature-blob: isaac-hail features/bands.feature 75115c7c7ff9302fd563ffb525e73c97e7d172e1 34
    feature-blob: isaac-hail features/send-addressing.feature bb1721453d51ce5d4412b7e320afee17b53dbf76 82
    feature-baseline: isaac-agent 928ff788bcb444b8e170fdeeafb08485a0895e3d
    feature-blob: isaac-agent features/session/default_frequencies.feature db5289eb885b00b066fe720a08b3b23a2193a705 79

The send-addressing blob is unchanged. It is repeated so this baseline's tree is the one in force. The agent blob is unchanged. Scenario line 109 is not this bean's. Do not edit it.

### Worker now

1. Rebase `bean/isaac-5gu1` (hail) onto `c8fbb23` if not already (`37fc7c4`). Feature diff may only drop `@wip` on bands line 34 and send-addressing line 82. Do not restore `prefer: 5`.
2. Keep the other five implementation branches. Land agent first, then the consumers, hail last among the modules that pin agent. Record each `main-sha`.
3. `bb bean-gate verify isaac-5gu1` exit 0 before landing hail. Do not edit the acp `:reach` prose. That stays with isaac-asik.

This note resets the verify-fail counter.

feature-baseline: isaac-hail c8fbb233919ec6ed2bd5098cbcdabf6bd379fec0
feature-blob: isaac-hail features/bands.feature 75115c7c7ff9302fd563ffb525e73c97e7d172e1 34
feature-blob: isaac-hail features/send-addressing.feature bb1721453d51ce5d4412b7e320afee17b53dbf76 82
feature-baseline: isaac-agent 928ff788bcb444b8e170fdeeafb08485a0895e3d
feature-blob: isaac-agent features/session/default_frequencies.feature db5289eb885b00b066fe720a08b3b23a2193a705 79

## Worker checkpoint (2026-09-28)

Done: isaac-agent removes reach schema/default, rejects default reach via config check; `bb verify` green; committed/pushed e98aee3 on bean/isaac-5gu1. Hail source fan-out removed in worktree, two acceptance scenarios un-WIP, not yet committed. Next: repair hail spec fixtures after removal; `bb spec` RED (parser broken in spec/isaac/hail/bands_spec.clj:9 and spec/isaac/config/hail_loader_spec.clj:55 from removal of reach-only lines). Resume at those files, run bb spec then bb verify; then gchat/cron/hooks/discord and repins, gate/land.

## Gate conflict (2026-09-28)

`bb bean-gate verify isaac-5gu1 --dir isaac-agent=../isaac-agent-5gu1 --dir isaac-hail=../isaac-hail-5gu1` exits 1: baselined `isaac-hail features/bands.feature` scenario at baseline line 109 expects `prefer: 5`, `Bad prefer type.`, stderr `prefer`; module main c8fbb23 changed this to `create: 5`, `Bad create type.`, stderr `create` after baseline. Restoring baseline scenario made `bb features features/bands.feature:109` fail (exit 0 vs expected 1); it cannot honestly be reverted. Restored module feature to module main version, leaving only @wip removals on worker branch. Planner must correct/rebaseline this contract. Implementation commits pushed: agent e98aee3, hail 37fc7c4, gchat cf4a62e, cron 797e986, hooks 29f9951, discord 8a1bd55; all six suites green prior to gate. No module main branches landed or bean completed.


## Worker checkpoint (2026-09-28, landing)

Done: corrected planner baseline is on this branch; gate PASS with agent/hail worktrees. Agent `bb verify` green and landed on main b1de8dc554ff9eefac90ea34e6e1902c642d2aaf. Cron, Discord, Gchat, Hooks, Hail branches rebased, pinned to landed agent in deps.edn and bb.edn, and pushed; cron/Discord integration failures from upstream behavior were fixed with tests. `bb ci` green for all five (gchat/hail before bb.edn pin, cron/Discord/hooks after). Next: land downstream repos from their clean bean branches, re-run gate then land Hail, update isaac/modules.edn and main-sha lines, complete bean. Resume from `../isaac-gchat-5gu1/bb.edn:1`: re-run `bb ci`, then squash/push gchat, cron, hooks, discord; `bb bean-gate verify isaac-5gu1 --dir isaac-agent=../isaac-agent-5gu1 --dir isaac-hail=../isaac-hail-5gu1` before Hail landing.

## Landed on main (2026-09-28)

main-sha: isaac-agent b1de8dc554ff9eefac90ea34e6e1902c642d2aaf
main-sha: isaac-gchat 8bf47707924b5678c8b655d972a7ec3235e28ffd
main-sha: isaac-cron 9c13a6a43c911f3d4595b31bca295615c75bbb9d
main-sha: isaac-hooks 3a6377051052d9a88cc6a368b62b576bcaee75ad
main-sha: isaac-discord 28ce56789d7570be1155174f76723a4b2b550ef7
main-sha: isaac-hail efa8f1909dda2cdec9ccf76b57bacb6a94a21957
main-sha: isaac 36061659bc9f337c8c6eeb2c7de2a940b1966c1a

All six module suites green; `bb bean-gate verify isaac-5gu1` PASS against the landed agent/hail main refs. ACP feature remains untouched for isaac-asik. The historical acceptance text mentioning `prefer: 5` was superseded by the planner's corrected baseline for `create: 5`.
