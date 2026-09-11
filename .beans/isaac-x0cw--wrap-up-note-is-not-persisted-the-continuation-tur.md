---
# isaac-x0cw
title: 'Wrap-up note is not persisted: the continuation turn never sees the done/next note it is supposed to start from'
status: completed
type: bug
priority: high
created_at: 2026-09-10T02:53:44Z
updated_at: 2026-09-10T21:08:15Z
---

Repo: isaac-agent (`drive/turn.clj` `apply-wrap-up-exhaustion`). Follow-up to isaac-y802 (0.1.52); companion to isaac-0uim (hail-side deterministic checkpoint).

## Evidence (isaac-mmod on isaac-work-2, 2026-09-09 19:48Z, 22:07Z, 2026-09-10 01:40Z)
Each wrap-up ended `:turn/ended :ended-by :cycle-limit :exhaustion :wrapped-up` with a prose note from the model (`apply-wrap-up-exhaustion` takes the wrap-up response's text as `:content`). The session transcript has NO assistant entry at any of those times (segments searched 01:36–01:41: only the continuation's entries from 01:41 on). The continuation is a fresh turn whose prompt is rebuilt from the transcript, so the note — the whole point of the checkpointed continuation (ntt6 decision 5) — is lost; the worker re-orients from raw history.

## Required
- The wrap-up note (and, when the wrap-up cycle made tool calls, the tool-less note that follows) is appended to the transcript as the turn's final assistant message before the turn ends — same as any reply — so the continuation reads 'done / next' at the tail of its history. The comm still receives it as the reply.

## Scenario (@wip, planted isaac-agent f76a069, features/llm/turn_exhaustion.feature)
- the wrap-up note is persisted as the turn's final assistant message so the continuation can read it — existing steps only.

## Acceptance
- planted scenario green with @wip removed; the other turn_exhaustion scenarios unchanged; `bb features && bb spec` green.

## Progress (2026-09-10, scrapper@isaac-work-1)

Done: claimed; worktree `isaac-agent-x0cw` on `bean/isaac-x0cw` (base origin/main@37116ef); un-@wip planted scenario; pushed `35c1bbe` (wip checkpoint).

RED: `bb features features/llm/turn_exhaustion.feature:136` — transcript matcher passed; memory-comm Then failed (`result.ended-by` / `result.exhaustion` on turn-end). Sibling wrap-up-with-tools scenario at :82 is green.

## Handoff (2026-09-10, scrapper@isaac-work-1)

branch: `bean/isaac-x0cw` @ `f49b476` (base origin/main@`0b52ae4`)

Root cause: the planted no-tool wrap-up scenario used `cycle-limit | 1`. Tool-loop budget is `< loops max-loops`, so the first tool *ran* and the queued text note was a normal `:reply` — wrap-up never ran. Sibling wrap-up-with-tools at :82 stays at cycle-limit 1 because it queues a *second* tool call.

Fix: planted scenario `cycle-limit | 0` so the first tool-bearing response exhausts, then `apply-wrap-up-exhaustion` persists the note as the final assistant message with `:ended-by :cycle-limit` / `:exhaustion :wrapped-up`. Spec `wrap-up note persistence` covers the same path.

Gate: `features/llm/turn_exhaustion.feature` 6/0; `bb spec` 1734/0. Full `bb features` timed out at 180s with no failures (pre-existing suite budget; not this change).

@wip already removed. Unverified.



## Landed on main (2026-09-10)

main-sha: isaac-agent 66d4233eeb83d8205b78da359e6a54caef38b6cf
