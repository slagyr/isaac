---
# isaac-0r95
title: Downstream repos read the new :defaults structure through the accessor
status: in-progress
type: feature
priority: high
created_at: 2026-09-22T22:05:14Z
updated_at: 2026-09-23T21:52:03Z
blocked_by:
    - isaac-ruom
    - isaac-ajlh
---

Repos: **isaac-episodes**, **isaac-hail**, **isaac-cron**, **isaac-hooks**, **isaac-server**.

isaac-ruom restructured `:defaults` into entity templates in isaac-agent +
isaac-foundation: `[:defaults :crew]` (the default crew id) moved to
`[:defaults :frequencies :crew]`, `[:defaults :model]` moved to
`[:defaults :crew :model]`, and the old flat keys are now retired (a validation
error). isaac-agent gained `isaac.config.defaults` — the one accessor ns for
these reads.

Downstream repos still read the old paths directly. Their CI is green only
because they pin pre-ruom shas; the moment an install's `isaac.edn` is migrated
by hand, these reads go nil.

## Readers to move onto `isaac.config.defaults`

- isaac-episodes: `src/isaac/episodes/lifecycle.clj:124`,
  `src/isaac/episodes/migrate.clj:92`, `src/isaac/episodes/cli.clj:90`,
  `src/isaac/recall/cli.clj:109`, `src/isaac/recall/tools.clj:27`
  (plus the `--crew` help text at `cli.clj:77`).
- isaac-hail: `src/isaac/hail/router.clj:266` and `:274` (`effective-crew`,
  `spawn-crew`) and the docstring at `:261`.

## Fixtures that still write the retired shape

- isaac-episodes: ~15 `.feature` files write `{:defaults {:crew "cordelia"}}`
  into `isaac.edn` (features/episodes/*.feature) — these now fail validation.
- isaac-hail: `spec/isaac/hail/router_spec.clj`,
  `spec/isaac/hail/delivery_worker_spec.clj`.
- isaac-cron: `spec/isaac/cron/service_spec.clj` (4 sites).
- isaac-hooks: `spec/isaac/hooks_spec.clj` (3 sites).
- isaac-server: `spec/isaac/configurator_steps.clj:118`.

## Migration table (from isaac-ruom)

| old | new |
|-----|-----|
| `[:defaults :crew]` | `[:defaults :frequencies :crew]` |
| `[:defaults :model]` | `[:defaults :crew :model]` |
| `[:tools :max-parallel]` etc. | `[:defaults :crew :tools …]` |
| `[:tools :defaults]` | `[:defaults :tools]` |

## Done when

- no downstream repo reads `[:defaults :crew]` / `[:defaults :model]` directly;
  each goes through `isaac.config.defaults`
- every fixture writes the new shape
- each repo repinned to the landed isaac-agent / isaac-foundation main shas,
  `bb ci` green
- this lands **before** any install's `isaac.edn` is migrated by hand

## Also in scope (planner, 2026-09-23)

isaac-gchat and isaac-gmail now read `[:defaults :crew]` directly (isaac-rfmh: gchat `handler/decide-opts` → `gate/decide` :default-crew; gmail `handler/crew`). When isaac-ruom lands those two must move to the accessor (`[:defaults :frequencies :crew]`) in the same train, or every Chat space and mailbox without its own crew silently falls back to main again. Add them to this bean's repo list.

## Defaults migration train (planner, 2026-09-23)

isaac-ruom landed today (isaac-foundation `97da637`, isaac-agent `28404cb`) and
**retired the old flat `:defaults` keys as validation errors**. That makes the
rest of this a train with a strict order, and the config edit is the LAST step,
not the first.

Current state: zanebot runs isaac-foundation `HEAD-97da637` with isaac-agent
still pinned at `831c5cf`. Its `isaac.edn` still carries the retired shape:

    :defaults {:crew :main :model :default :stream-idle-timeout-ms 300000}

This validates **only because the agent half is not deployed** — the retirement
lives in isaac-agent's manifest schema, not foundation's. Bumping the agent pin
before the steps below makes zanebot's config invalid.

### Order

1. **isaac-0r95** — seven downstream repos move their `[:defaults :crew]` /
   `[:defaults :model]` reads onto `isaac.config.defaults`, fixtures updated,
   each repinned to the ruom shas. Dispatched to a subagent 2026-09-23.
   isaac-gchat and isaac-gmail are the behavioural ones: their reads decide a
   fallback crew, so going nil means every Chat space and mailbox without its
   own crew silently falls back to `main`.
2. **Repin + re-green** those repos' registry entries in `isaac/modules.edn`.
3. **Migrate zanebot's `isaac.edn`** `:defaults` to the new shape. Placement of
   `:stream-idle-timeout-ms` needs confirming against the ruom schema rather
   than guessing.
4. **Bump zanebot's isaac.agent pin** to a ruom-containing sha.

### Open decision at step 4: the agent version line

zanebot is pinned to `831c5cf`, which is **not on main** — it lives on
`origin/hotfix/isaac-dgod-agent-0.1.81`. That branch carries two release commits
absent from main (`e0ced4d` 0.1.80, `831c5cf` 0.1.81) whose *content* is on main
as different commits. So:

- hotfix line declares **0.1.81**
- agent main tip `28404cb` declares **0.1.80**

Deploying main tip as-is regresses the declared version, which `modules list`
reports and module requirements check against. Options: cut a release commit on
agent main bumping to 0.1.82, or fold the hotfix line back into main first.
**Micah's call — not taken.**

### Already done on zanebot (2026-09-23)

- foundation upgraded `01d81b9` → `7b2f053` (isaac-49zp + isaac-h2ck) →
  `97da637` (isaac-ruom foundation half). Zero errors, 11 modules ok.
- `:modules` and `:tz` split into their own slice files under `config/`
  (isaac.edn 160 → 114 lines). Config valid, all 11 modules resolve.
- Backups: `~/isaac-config-backup-20260923-cfgtree.tgz`,
  `isaac.edn.bak-20260923-modsplit`, `isaac.edn.bak-20260923-tzslice`.

## Train halted at a ruom regression in foundation validation (worker, 2026-09-23)

Four repos landed; three are blocked by one upstream bug, not by anything in
this bean.

### Landed on main

| repo | main-sha | specs | features |
|------|----------|-------|----------|
| isaac-gchat | `041f5bc3bf9b614621befd5734b10e20b7b5ab9e` | 173 / 0 | 54 / 0 |
| isaac-gmail | `465f793cc92ec13713fa97fd5012099fae39c229` | 51 / 0 | 13 / 0 |
| isaac-hail | `9ddf7f154dbce6fbf40363ffeff5ce5702b3f199` | 173 / 0 | 136 / 0 (2 pending, pre-existing) |
| isaac-cron | `0f47e64122bfad29a6b9c920dec3e100315da6db` | 23 / 0 | 22 / 0 |

### Blocked — work pushed to `bean/isaac-0r95`, not landed

- **isaac-episodes** — specs 215 / 0, features 85 / **15**.
- **isaac-http** (the live repo behind the bean's "isaac-server") — specs
  193 / **6**, features not reached.
- **isaac-hooks** — specs 30 / 0, features 20 / **2**.

### The blocker

isaac-ruom's foundation half added `demands-a-field?` to
`src/isaac/config/validation.clj`: when a `:map` spec declares any field with
`:present?`, validation now descends into the map **even when it is absent**,
so it can say "the default crew is missing" for an omitted
`:defaults :frequencies`. The rule is global, so every module schema with an
*optional* nested map whose inner fields are `:present?` now errors whenever
that map is left out.

- isaac-episodes `resources/isaac-manifest.edn`: `:episodes :embedding`
  (`:description "Optional embedding capability"`, inner `:api` and `:model`
  both `:present?`) → `episodes.embedding.api/.model is required` on any
  config without `:embedding`.
- isaac-http `resources/isaac-manifest.edn`:
  `:http :auth :principals <id> :previous` (inner `:hash` `:present?`) →
  `http.auth.principals.<id>.previous.hash is required` for any principal
  without a rotation overlap. That breaks `auth-cli mint!/rotate` and, in
  isaac-hooks, makes the hot reload after `persist-principal!` get rejected,
  so the isaac-4o6r scenarios 401.

Bisected in isaac-http: green at foundation `7b2f053` (isaac-49zp), red at
`97da637` (isaac-ruom). Nothing to do with the `:defaults` move — the same
failures appear with the pre-migration fixtures.

**Decision needed (Micah's):** narrow `demands-a-field?` in foundation (and
re-land foundation + repin agent), or relax `:present?` on those optional
nested maps in each module. Not taken by the worker.
