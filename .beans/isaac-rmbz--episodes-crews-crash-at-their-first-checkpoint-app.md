---
# isaac-rmbz
title: Episodes crews crash at their first checkpoint (append-checkpoint! missing)
status: todo
type: bug
priority: critical
created_at: 2026-09-30T00:34:07Z
updated_at: 2026-09-30T00:34:07Z
---

Likely repo: **isaac-episodes**. Live on zanebot: any episodes crew with `:cycle :checkpoint-every` crashes at its first checkpoint.

## Why

Field, 2026-09-30 00:07Z, Mixmaster (isaac-work-4) on isaac-0lb7:
`AbstractMethodError: EpisodesPolicy does not define … append_checkpoint_BANG_`.
Agent's `SessionPolicy` gained `append-checkpoint!` (isaac-tic5); the
episodes deftype never implemented it. Crews without `:cycle` never
checkpoint, so it went unseen until a worker crew moved to episodes. Second
protocol gap in episodes today (the first was `prepare-turn!`, fixed by
isaac-1vx0), and a deftype missing a method compiles fine and fails only
when called.

## Design

- `EpisodesPolicy` implements `append-checkpoint!` by delegating to the
  store, like `append-reckoning!`.
- Guard spec: a unit spec asserts `EpisodesPolicy` implements **every**
  method of `isaac.session.policy/SessionPolicy`, derived from the
  protocol itself (e.g. `(:sigs policy/SessionPolicy)` checked against
  the class's declared methods), not a hand-kept list. The next agent
  protocol change turns episodes' own suite red instead of a live crew.

## Acceptance

- isaac-episodes `features/episodes/live.feature` — "an episodes crew with a checkpoint cadence checkpoints and finishes its turn"
- Spec: EpisodesPolicy implements every SessionPolicy method (fails today on `append-checkpoint!`).

feature-baseline: isaac-episodes f1f7142ca8b5cbf0f17710ae8935a9ffe6f90016
feature-blob: isaac-episodes features/episodes/live.feature 8d8ee685dbd12a07d079ea30a153dc249253beaa 825
