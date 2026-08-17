---
# isaac-l7lv
title: 'Flush transcript mid-loop: persist each toolCall/toolResult as it happens'
status: draft
type: feature
priority: high
tags:
    - isaac-agent
    - transcript
created_at: 2026-08-17T22:44:47Z
updated_at: 2026-08-17T22:44:47Z
parent: isaac-wq8m
---

Decision (2026-08-17, Micah): transcript should be written whenever possible. The atom-then-flush in `record-tool-call!` / `run-tool-calls!` is the wrong shape.

## Problem

`execute-llm-turn!` appends the user line immediately, then accumulates every tool pair in an `executed-tools` atom. `run-tool-calls!` dumps the pairs only after `tool-loop/run` returns (or on cancel). Mid-turn the jsonl and sidecar are frozen.

Witnessed on zanebot `tono-work-1` (tono-by25, 2026-08-17): `~/.isaac/logs/server.log` showed a live grok-4.6 `/v1/responses` loop (body 20kB → 267kB) while `tono-work-1.jsonl` still ended at the user prompt. Operators cannot watch a turn. A kill mid-loop loses every completed tool cycle; resume then has nothing to repair except the user line.

## Design

Write the transcript as soon as the fact exists. Next LLM cycle still comes from the in-memory follow-up (`api/followup-messages`), not a transcript reread — persist is a side effect.

| When | Write |
|---|---|
| User input accepted | user (already) |
| Model returns a tool call | assistant `toolCall`, **before** exec |
| Tool returns | `toolResult` immediately |
| Final text / error | assistant / error (already) |
| Cancel mid-exec | leave the `toolCall`; resume already synthesizes a result for dangling calls |

Drop the end-of-turn `run-tool-calls!` dump so we do not double-write. Cancel path must not write the same pairs again.

## Likely repo scope

isaac-agent: `src/isaac/drive/turn.clj` (`record-tool-call!`, remove post-loop `run-tool-calls!` persist). Specs in `spec/isaac/drive/turn_spec.clj`.

## Proposed acceptance (not committed — keep draft until these exist)

- `record-tool-call!` appends the `toolCall` entry before invoking the tool. A tool that reads the session transcript mid-exec sees its own `toolCall`.
- After the tool returns, the next transcript entry is the matching `toolResult` (same id).
- Completing a multi-tool turn does not duplicate those pairs (`run-tool-calls!` at the end is gone or no-ops already-persisted pairs).
- Cancel after `toolCall` is written, before result: transcript has the `toolCall` and no result (resume synthesizes).
- Runnable once specs land: `bb spec spec/isaac/drive/turn_spec.clj` plus a feature if we add one under `features/session/` that inspects the jsonl after the first tool of a grover turn (needs a hook or a tool that snapshots the file).

## Relationship

Child of isaac-wq8m (turn resilience). Complements isaac-7li9 (durable turn marker) and isaac-vdfc (startup resume / dangling tool-result repair): those assume the tail is on disk.
