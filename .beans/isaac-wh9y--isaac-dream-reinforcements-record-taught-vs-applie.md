---
# isaac-wh9y
title: 'isaac-dream: reinforcements record taught vs applied, and dreams record contradictions'
status: todo
type: feature
priority: normal
tags:
    - dream
created_at: 2026-11-16T14:49:07Z
updated_at: 2026-11-16T14:49:07Z
parent: isaac-pcm3
blocked_by:
    - isaac-gomk
---

Repo: **isaac-dream**. Split from isaac-o4di (Micah, 2026-11-15/16). Spec: `features/dream/kinds.feature`.

## What

The dream answer learns to say *how* a habit came up:

- `reinforce` items carry `kind`: `taught` (the window teaches the lesson again; the default when absent) or `applied` (the crew's own reply follows the habit with no correction after it). Applied evidence must cite at least one assistant line, otherwise the item is dropped and noted in the diary (`reason applied-without-reply`).
- A third list, `contradict: [{id, evidence}]`, for active habits the window argues against (a person corrects the behavior, or following it goes wrong). Code validates evidence like a reinforcement, appends a `:contradicted` history event, lists the id under the diary's `:contradicted`, and the digest names the habit with `dream retire <id> --crew <crew>`. The habit's state does not change here (retirement is the retire bean).
- History events: reinforcements record `:kind :taught|:applied`.
- The dream prompt (`resources/isaac/dream/prompt.md`) explains both kinds and the contradict list, with the JSON shape.

Strength weights for these events (taught +1, applied +1, contradicted −4) live in isaac-o4di; this bean only records the events.

## Acceptance

`bb features features/dream/kinds.feature` green with `@wip` removed (all 5 scenarios), plus `bb spec` and full `bb features` green. Bump the module version.

feature-baseline: isaac-dream 83923e51b7f5114d36b7db84a1c6cf384d06d1c8
feature-blob: isaac-dream features/dream/kinds.feature aae873207a82b96002165e5e792fd2c672563689

## Planner note (2026-11-16): version bumps

Sibling dream beans land concurrently. When rebasing onto main conflicts only on `resources/isaac-manifest.edn` `:version`, take the higher of main's and yours, bump its patch by one if they are equal, keep every schema key from both sides, and carry on without stopping for the planner.
