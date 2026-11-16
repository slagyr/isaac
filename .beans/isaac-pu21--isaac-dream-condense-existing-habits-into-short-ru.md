---
# isaac-pu21
title: 'isaac-dream: condense existing habits into short rules as approvable revisions (keep id, history, strength)'
status: in-progress
type: feature
priority: normal
tags:
    - dream
created_at: 2026-11-16T16:22:45Z
updated_at: 2026-11-16T16:46:49Z
parent: isaac-pcm3
blocked_by:
    - isaac-4wsu
---

Repo: **isaac-dream**. Follows isaac-4wsu (short rules, why, dream__habit; landed, deployed in 0.4.0). Micah approved Zane's 38 habits on 2026-11-15 with long lessons (avg ~420 chars, backstory included, no why). Spec: `features/habits/revise.feature`. Decisions (Micah, 2026-11-16): "long" means over 300 characters or no why; a revision leaves strength and decay untouched.

## What

- **`isaac dream condense --crew <id>`**: one model call over the crew's **active** habits whose lesson is over 300 characters or that have no `:why`. Other habits are not sent. The model answers `{"revise": [{"id", "lesson", "why"}]}`.
- **Pending revision**: each valid item is stored on the habit as `:revision {:lesson … :why …}` with a `:revision-proposed` event (`:by :condense`). The habit keeps its id, state, current lesson (still the one in the prompt), evidence, history and strength. The digest lists the revisions with `dream approve <id> --crew <crew>`.
- **Too long**: a revised lesson over 300 characters is dropped; condense prints the habit id and `lesson-too-long` on stdout; nothing is stored on the habit.
- **`dream approve <id>`** on a habit with a pending revision applies it: `:lesson`/`:why` from the revision, `:revision` removed, a `:revised` event with `:previous <old lesson>`. No strength change (revision events weigh 0).
- **`dream reject <id>`** on a habit with a pending revision drops the revision and appends `:revision-rejected`; the habit's state and lesson stay as they were (it is NOT rejected).

Out of scope: the nightly dream proposing revisions (`revise` in the dream answer). Later, if wanted.

## After deploy (planner)

Run `isaac dream condense --crew zane` on zanebot, review the 38 revisions with Micah, approve.

## Acceptance

`bb features features/habits/revise.feature` green with `@wip` removed (4 scenarios), plus `bb spec` and full `bb features` green. Do not bump the module version (the deploy train does).

feature-baseline: isaac-dream 2f3a4693c65dfe1fa14de7f98b3b9122e9370a97
feature-blob: isaac-dream features/habits/revise.feature 14a70510f7bd2696219c86bb4751804d75376559

## Acceptance conflict (2026-11-16)

Implementation is committed at `isaac-dream` `bean/isaac-pu21` (`254d5dc`). `bb spec` passes (53 examples); `bb features features/habits/revise.feature` has 4 examples, 1 failure; full `bb features` has 76 examples, 1 failure. `bb bean-gate verify isaac-pu21` returned:

```
isaac-pu21 bean-gate: PASS (isaac-dream @ HEAD 254d5dc (branch bean/isaac-pu21))
```

The failing assertion is `features/habits/revise.feature:88`: `Then the stdout matches: Strength: 3\\.0`. The scenario creates a `greenhouse` session via Background; that session has a current-day chronicle entry. Existing `habits/active-days` includes this day and `habits/strength` applies its configured 30-day half-life to the 2026-04 approved and reinforced events. Actual strength is 2.9314799053027376, rendered `Strength: 2.9`. `:revised` has zero weight and the revision leaves decay untouched as explicitly required in the bean. Making 3.0 pass would require changing decay semantics or the baselined scenario's fixture/assertion. Neither is a worker-authorized contract edit. Planner should correct and re-baseline the scenario (e.g. expect 2.9 under its current-day session fixture), then redispatch. No `.feature` edits were made except removing `@wip`.

## Planner decision (2026-11-16): strength scenario re-cut

The worker was right: `revise.feature` "approving a revision…" expected `Strength: 3.0`, but the test session counts as an active day after the April events, so default decay prints 2.9. The scenario now pins `dream.strength.half-life-days` to 100000 for that scenario (dream main 13cc0bc) and is re-baselined above; nothing else changed. Rebase onto main (the feature-file change merges with your @wip removal), rerun the four scenarios and `bb ci`, gate, land.

feature-baseline: isaac-dream 13cc0bcf654abd32cb3dce5eccd64887dbcb2371
feature-blob: isaac-dream features/habits/revise.feature e8fe0b8e446e37897f794642540abb01e9c6ff4e

## Planner decision (2026-11-16): strength scenario re-cut

The worker was right: `revise.feature` "approving a revision…" expected `Strength: 3.0`, but the test session counts as an active day after the April events, so default decay prints 2.9. The scenario now pins `dream.strength.half-life-days` to 100000 for that scenario (dream main 13cc0bc) and is re-baselined above; nothing else changed. Status was reset to todo only so the baseline could be cut; re-claim it. Rebase onto main (the feature-file change merges with your @wip removal), rerun the four scenarios and `bb ci`, gate, land.

## Landed on main (2026-11-16)

main-sha: isaac-dream 7c1b4dfdfd2b2a2da495f2de27d9ebbbee8d7483

`bb features features/habits/revise.feature`: 4 examples, 0 failures; `bb ci`: 53 specs and 76 features green; `bb bean-gate verify isaac-pu21`: PASS on squash commit.
