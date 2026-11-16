---
# isaac-pu21
title: 'isaac-dream: condense existing habits into short rules as approvable revisions (keep id, history, strength)'
status: todo
type: feature
priority: normal
tags:
    - dream
created_at: 2026-11-16T16:22:45Z
updated_at: 2026-11-16T16:22:45Z
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
