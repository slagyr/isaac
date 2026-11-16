---
# isaac-gomk
title: 'isaac-dream: reinforce or supersede existing habits instead of re-proposing duplicates'
status: draft
type: feature
priority: normal
tags:
    - dream
created_at: 2026-11-16T03:07:30Z
updated_at: 2026-11-16T03:07:30Z
parent: isaac-pcm3
---

Repo: **isaac-dream**. Found dreaming Zane's whole history week by week on zanebot, 2026-11-15 (Opus, 21 weekly runs, 64 habits proposed). Spec: `features/dream/dedupe.feature`.

## Problem

The dream re-proposed lessons it already had, in new words, instead of reinforcing or superseding them: four near-identical "a silent cron job still owes one auditable line" habits from four weeks, eight "check health data freshness" variants, three "prove a scheduled fix by running it". Each run saw the earlier ones in its prompt. Reviewing 52 proposals to throw most away defeats the point, and duplicates split a habit's evidence across ids.

## Fix

- **Prompt** (`resources/isaac/dream/prompt.md`): compare against current habits (all states are listed with their state). Same lesson → `reinforce` it. A sharper version → propose with `supersedes`. Two active habits that say the same thing → one new habit that supersedes both. Only a genuinely new lesson gets a new id.
- **Code**: a `propose` item whose id names an existing habit reinforces that habit (evidence validated as usual; lesson unchanged) instead of being silently dropped (today `propose!` throws "Habit already exists" and the item vanishes).

Reinforcing a proposed habit already works (`habits/reinforce!` accepts active and proposed).

## Acceptance

`bb features features/dream/dedupe.feature` green with `@wip` removed (both scenarios), plus `bb spec` and full `bb features` green. Bump the module version.

Measure after deploy (not a gate): re-dream Zane's history into a scratch root on zanebot and compare proposal count and duplicate clusters against the 2026-11-15 run.
