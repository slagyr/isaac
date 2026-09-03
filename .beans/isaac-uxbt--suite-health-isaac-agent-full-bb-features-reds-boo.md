---
# isaac-uxbt
title: 'Suite health (isaac-agent): full bb features reds — boot, compaction_template, episodes/live'
status: draft
type: bug
priority: high
tags:
    - suite-health
created_at: 2026-09-03T23:35:31Z
updated_at: 2026-09-03T23:35:31Z
---

Ambient feature reds on `isaac-agent` main that fail the full `bb features` gate for beans whose own surface is green. Filed 2026-09-03 by planner while adjudicating **isaac-vuto**; the same four reds are the reason vuto's `bb features && bb spec` clause was dropped.

This bean owns the reds. No bean whose product surface is elsewhere should carry them.

## Observed (2026-09-03, scrapper@isaac-work-2, isaac-agent `4b7c8ac`)

`bb features && bb spec` fails with 4 feature failures; every focused gate on the same tree is green (`token_accounting.feature` 7/0, `compaction_overflow.feature` + `compaction_logging.feature` 18/0, `bb spec spec/isaac/session spec/isaac/drive` 352/0).

| # | file | failures | note |
|---|------|----------|------|
| 1 | `features/session/boot.feature` | 2 | no owning bean; not touched by vuto |
| 2 | `features/session/compaction_template.feature` | 1 | template contract landed by **isaac-os7r** (`config/compaction.md` replaces the built-in); previously seen red at `:49` in the isaac-jarr full run |
| 3 | `features/episodes/live.feature` | 1 | short-transcript compaction fixture; the `x2up`/`p9zy` estimator family already forced this gate off **isaac-mrfu** |

## Prior observations (isaac-jarr full run, 2026-09-03, main `c2b7b9e`, unwrapped 747/5 in 228s)

Same class, recorded here so the next full-gate investigation has one place to start. Some may already be closed.

| file:line | symptom | relation |
|-----------|---------|----------|
| `session/context_window_guard.feature:74` | bulletin `compaction/disabled` | 5nxf migration of the old `compaction-disabled` event; isolated scuttlebutt green |
| `session/compaction_memory_flush.feature:42` | compaction-turn `memory__write` did not persist | feature unchanged since `13da406` (pre-5nxf) |
| `comm/scuttlebutt.feature:91` | streaming tool-progress | isolated file 4/0/6; red only in the full suite — ordering / state leak |
| `bridge/cancel_aborts_work.feature:27` | turn result expected `"cancelled"`, got nil | cancel-race flake family: **isaac-x27m**, **isaac-zcb9**, **isaac-2bni**; jarr carries a planner exception for it |
| `session/compaction_template.feature:49` | `config/compaction.md` did not replace the built-in template | same as row 2 above; **isaac-os7r** |

## What this bean must produce

For each of the three currently-observed reds:

1. Reproduce the failure in isolation (`bb features <file>`) on current `isaac-agent` main. Record whether it reproduces isolated or only in the full suite — an only-in-suite red is an ordering/state-leak defect, not a scenario defect.
2. Name the cause: product regression, fixture drift behind a landed contract, or cross-scenario state leak.
3. Fix the product or the fixture. **Do not** weaken scenario intent to make the suite green, and do not `@wip` a scenario without a dedicated bean owning its return.
4. If a red belongs to an existing owner (`isaac-os7r` for the template, the `x2up`/`p9zy` estimator family for `live.feature`), hand it there and record the handoff instead of duplicating the work.

## Acceptance

    cd isaac-agent
    bb features features/session/boot.feature
    bb features features/session/compaction_template.feature
    bb features features/episodes/live.feature

0 failures on each, on current main, with no new `@wip` tags. Then:

    clojure -M:features

unwrapped, exit 0 — or, if reds remain, each remaining red is named in this bean with an owning bean id. Record wall time; the 180s `bb features` wrapper timeout under load is not by itself a red.
