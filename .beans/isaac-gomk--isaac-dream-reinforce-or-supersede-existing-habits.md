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

Repo: **isaac-dream**. Found dreaming Zane's whole history week by week on zanebot, 2026-11-15 (Opus, 21 weekly runs, 64 habits proposed).

## Problem

The dream re-proposes lessons it already has in new words instead of reinforcing or superseding them. Zane ended up with:

- 4 near-identical "a silent cron job still owes one auditable line" habits (`one-auditable-line-per-cron-run`, `cron-line-identifies-this-run`, `cron-reply-names-this-send`, `cron-send-line-names-the-delivery`) from four different weeks, each run seeing the earlier ones in its prompt.
- 8 variants of "check health data freshness before alerting".
- 3 of "prove a scheduled fix by running it".

Reviewing 52 proposals by hand to throw most of them away defeats the point. Duplicates also split the evidence a habit has earned across several ids.

## Direction (to settle before todo)

- **Prompt:** before proposing, compare against current habits (proposed and active). Same lesson → `reinforce` it. Sharper version of an existing one → propose with `supersedes`. Only a genuinely new lesson gets a new id.
- **Reinforce proposed habits too?** Today reinforcement lands on proposed habits as well as active ones, though isaac-p36b said active only. Decide which is intended; reinforcing proposals is probably what makes the dedupe work.
- **Maybe a consolidate pass:** `isaac dream consolidate --crew <id>` (or a step at the end of a long `--since` run) asks the model once to merge near-duplicate proposals into one with the union of their evidence, retiring the rest. Only if the prompt change alone isn't enough.

## Acceptance (draft)

- A scenario where the window repeats a lesson that is already a proposed habit: the dream reinforces it, proposes nothing new.
- A scenario where the window sharpens a proposed habit: the new proposal lists it in `supersedes`.
- Measure: re-dream Zane's history into a scratch root and compare proposal count and duplicate clusters against the 2026-11-15 run.
