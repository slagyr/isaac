---
# isaac-o4di
title: 'isaac-dream: habit strength — weighted, once a day, decays over active days; strongest first within a budget; pin'
status: completed
type: feature
priority: normal
tags:
    - dream
created_at: 2026-11-16T03:10:41Z
updated_at: 2026-11-16T15:04:16Z
parent: isaac-pcm3
---

Repo: **isaac-dream**. Micah, 2026-11-15/16: habits should decay when not reinforced, lose a lot of priority on a complaint, and feel natural; decay must be weighed by interaction, not calendar time, and one day of heavy use is one point, not a hundred. Spec: `features/habits/strength.feature`. Split: kinds of reinforcement → isaac-wh9y; retirement → isaac-tpa5.

## Strength

Computed from a habit's history on read; never stored, so weights can be retuned and every habit re-scores.

| event | weight |
|---|---|
| `:approved` | +2 |
| `:reinforced` (no `:kind`, or `:kind :taught`) | +1 |
| `:reinforced` `:kind :applied` | +1 |
| `:contradicted` | −4 |

- Each kind counts at most once per habit per UTC day.
- An event's weight halves for every `dream.strength.half-life-days` (default 30) **active days** after the day it happened. An active day is a UTC day on which any of the crew's sessions has a transcript entry, whoever started the turn (a person, another crew, a subagent, a hail, the crew's own cron or autonomous work). Days with no activity never decay anything.
- Weights live under `dream.strength.*` on the crew dream schema (defaults from `defaults.crew.dream`): `half-life-days` is required by the spec; the event weights may be config too.

## Habits section

- Active habits, **pinned first**, then by strength, highest first.
- `dream.prompt-chars` (default 6000) caps the **lesson text**; the section stops before the lesson that would pass it. Pinned lessons always appear and count toward the budget. Habits left out stay active.

## Pin

- `isaac dream pin <id> --crew <crew>` sets `:pinned true` and appends `:pinned`; `dream unpin` sets `:pinned false` and appends `:unpinned`. Pin events carry no weight.
- `isaac dream show` prints `Strength: <n.n>` and `Pinned: yes|no`.

Later, if good habits fade during solo stretches: opportunity-based decay (the dream notes which habits had a chance to apply; only those decay). Not in this bean.

## Acceptance

`bb features features/habits/strength.feature` green with `@wip` removed (all 9 scenarios), plus `bb spec` and full `bb features` green. Bump the module version.

feature-baseline: isaac-dream 83923e51b7f5114d36b7db84a1c6cf384d06d1c8
feature-blob: isaac-dream features/habits/strength.feature 15fa4dea5940afaa1e078d7aecffe01592bb1f37

## Work checkpoint (2026-11-16)

Done: implemented computed, deduplicated habit strength with crew transcript active-day decay, pinned-first lesson budgeting, pin/unpin/show CLI, schema and version 0.3.0. `bb ci` passed (39 specs, 52 feature examples); all 9 strength scenarios passed; `bb bean-gate verify isaac-o4di --dir isaac-dream=../isaac-dream-o4di` passed on 5d12611. Branch `bean/isaac-o4di` pushed.

Next: landing blocked at `resources/isaac-manifest.edn:2`: rebasing bean branch onto `origin/main` (ef32962, isaac-gomk) conflicts in module version/schema. Rebase aborted, branch unchanged and pushed. Planner must coordinate overlapping manifest changes before landing. Resume at `resources/isaac-manifest.edn:2` after planner decision; then rebase, `bb ci`, gate, squash and complete.

## Planner decision (2026-11-16): manifest conflict

The conflict at `resources/isaac-manifest.edn` is only the version line: main has `0.2.3` (isaac-g2zv, isaac-gomk), this branch `0.3.0`. Resolve by keeping this branch's schema additions (`:prompt-chars`, `:strength`) and version `0.3.0` (higher than main). Then rebase, `bb ci`, gate, squash and land.


## Landed on main (2026-11-16)

main-sha: isaac-dream 3600e65ab1778414e805308923c049dac9dbb431

Rebase conflict in resources/isaac-manifest.edn was version-only (ours 0.3.0, landed gomk 0.2.3). Kept 0.3.0 and the strength schema keys; no handbook/prompt overlap. bb ci green, bean-gate PASS on the squash.
