---
# isaac-0r95
title: Downstream repos read the new :defaults structure through the accessor
status: todo
type: feature
priority: high
created_at: 2026-09-22T22:05:14Z
updated_at: 2026-09-23T20:57:54Z
blocked_by:
    - isaac-ruom
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
