---
# isaac-l7lv
title: 'Flush transcript mid-loop: persist each toolCall/toolResult as it happens'
status: in-progress
type: feature
priority: high
tags:
    - unverified
    - isaac-agent
    - transcript
created_at: 2026-08-17T22:44:47Z
updated_at: 2026-08-17T23:19:16Z
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

## Acceptance

Add these to `isaac-agent/spec/isaac/drive/turn_spec.clj` (describe `record-tool-call!` / execute-llm-turn). TDD: red first.

1. **toolCall lands before exec** — a stub tool that reads the session transcript mid-invoke sees its own assistant `toolCall` entry (same id). `bb spec spec/isaac/drive/turn_spec.clj:LINE`
2. **toolResult lands immediately after** — when the tool returns, the next transcript entry is `toolResult` with that id. Same file.
3. **no double-write** — a turn that runs two tools then a final assistant has exactly two `toolCall`/`toolResult` pairs, not four. Post-loop `run-tool-calls!` dump is gone (or no-ops already-persisted pairs).
4. **cancel mid-exec** — after `toolCall` is written, tool reports `:cancelled`: transcript has the `toolCall` and no matching result (resume synthesizes).

Verify: `bb spec spec/isaac/drive/turn_spec.clj` green; `bb spec` and `bb lint` on touched files.

Likely repo: isaac-agent.

## Relationship

Child of isaac-wq8m (turn resilience). Complements isaac-7li9 (durable turn marker) and isaac-vdfc (startup resume / dangling tool-result repair): those assume the tail is on disk.

## Implementation notes (scrapper@isaac-work-1)

- `record-tool-call!` now takes `:ctx` and writes:
  - assistant toolCall **before** `tool-fn*` runs
  - toolResult immediately after a non-cancel return
- Cancel (`:error :cancelled`) still throws; toolCall stays, no toolResult.
- `execute-llm-turn!` no longer dumps `@executed-tools` via `run-tool-calls!`
  on success or cancel (avoids double-write). Atom kept for loop bookkeeping.
- `run-tool-calls!` remains as a legacy pair-dump for any leftover callers.
- Commit: isaac-agent `875ca58`.
- Verified: `bb spec spec/isaac/drive/turn_spec.clj` — 37 examples, 0 failures.

## Verify fail (attempt 1, 2026-08-17): lint gate failed on touched files; bb lint reported unresolved speclj macros in spec/isaac/drive/turn_spec.clj

## Lint repair (scrapper@isaac-work-2, 2026-08-17)

`bb lint` on touched files now 0/0. Changes in isaac-agent **3325c2d**:
- `spec/isaac/drive/turn_spec.clj`: explicit speclj `:refer` list (describe/it/should*); drop unused `sidecar` require
- `src/isaac/drive/turn.clj`: `_session-key` in `compaction-estimate-opts`

Re-verified: `bb spec spec/isaac/drive/turn_spec.clj` 37/0/121; `bb lint src/isaac/drive/turn.clj spec/isaac/drive/turn_spec.clj` 0 errors, 0 warnings.
