---
# isaac-3ljt
title: Sealing leaves injected recall out of scenes and gists
status: todo
type: bug
priority: normal
created_at: 2026-09-29T23:32:13Z
updated_at: 2026-09-29T23:35:19Z
blocked_by:
    - isaac-jvwr
---

Likely repo: **isaac-episodes**. Design: Micah + planner, 2026-09-29.

## Why

Live gists on Zane's store show recall echoing itself: "Retrieving context
from earlier discussions…", "The user recalled an earlier request…". The
injected recall block is sealed into new scenes, those scenes are recalled
later, and memories about remembering crowd out real ones. Gists should
describe only what the episode itself said and did.

## Design (agreed)

- At seal / distill time, remove the injected recall and lineage blocks
  (header `[Recalled memory; not a request]` / "Recalled from earlier
  conversations" / "Previously in this conversation") from what the
  distiller and the scene text see. Only new material is segmented,
  gisted, embedded.
- After the sibling bean the block is a **prefix of the opening user
  message**, not its own entry: strip the prefix, keep the prompt. Handle
  the legacy shape too (a standalone recall entry in already-open
  episodes) by dropping it whole.
- No change to what the model receives during the turn.

## Acceptance

- isaac-episodes `features/episodes/live.feature` — "sealing leaves recalled memory out of the new scenes"
- Every other scenario in `features/episodes/live.feature` stays green.

feature-baseline: isaac-episodes ae6db393b3a1ee4dcd3f1cad20c178b7fff203ef
feature-blob: isaac-episodes features/episodes/live.feature 7e96ef19a21d5812b57771cc396873a4e1bc1ea7 408

## Planner note (2026-09-29)

Today the scenario fails at the `an episode exists` step: the new episode seals as `:partial`, because the standalone recall entry makes three distilled entries and the queued gist `1-2` leaves entry 3 flagged. With recall kept out of the seal, the episode has two entries and closes clean. The `does not contain` rows then cover the jvwr shape, where recall is a prefix of the prompt message.
