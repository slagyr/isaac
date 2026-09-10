---
# isaac-x0cw
title: 'Wrap-up note is not persisted: the continuation turn never sees the done/next note it is supposed to start from'
status: in-progress
type: bug
priority: high
created_at: 2026-09-10T02:53:44Z
updated_at: 2026-09-10T03:09:57Z
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
