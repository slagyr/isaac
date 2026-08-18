---
# isaac-92h
title: 'Mid-turn compaction: check context during tool loops'
status: in-progress
type: feature
priority: high
created_at: 2026-04-15T13:52:12Z
updated_at: 2026-08-18T05:42:46Z
---

Priority (2026-08-17, Micah): mid-turn compaction is required. A long hail turn can blow the context window because the live loop lives only in `@current-request` and compaction never sees it.

Witnessed on zanebot `tono-work-1` / tono-by25: turn-start estimate **5,149 tokens**; same turn's `/v1/responses` bodies **20 kB → 627 kB** with no `:drive/context-exhausted`. Isaac was blind.

## What is true today

- `check-compaction!` runs **once**, at turn start, against `store/get-transcript`.
- `tool-loop/run` never estimates or compact.
- `followup-fn` rebuilds `:messages` from the in-memory response + tool results — that is the prompt that grows.
- **isaac-l7lv** (completed) now writes each `toolCall`/`toolResult` to the store as it happens. Mid-turn estimate can finally see the growing head **if** we check the store after persist. Zanebot is not on that agent SHA yet; this bean still lands in isaac-agent.

## Design (settled 2026-08-17, planner)

1. **When:** after each tool-result persist, before the **next** LLM call. Not async (`start-async-compaction!` is turn-start only). Same `should-compact?` threshold as turn start.
2. **What:** compact the **disk** transcript (now includes mid-loop pairs via l7lv).
3. **Critical — rebuild the in-memory request.** After a successful mid-turn compact, **do not** send `followup-fn`'s accumulated `:messages`. Rebuild `:messages` from `prompt.builder` over `active-transcript` (post-compaction) and put that on `@current-request` / the next `chat-fn` request. Otherwise we compact the store and immediately re-send the fat loop.
4. **Where:** isaac-agent. Hook after `persist-tool-result!` (or at the top of the next `tool-loop` iteration before `chat-fn`). Keep the loop provider-agnostic; drive/turn owns rebuild + compact, or tool-loop gets a `:after-tools` hook. Do not put session/store knowledge in adapters.
5. If compact cannot save (disabled / no-progress) and the next request would cross the existing 0.98 guard, take the same `:context-exhausted` path as turn start.

## Out of scope

- Parallel *execution* of a tool batch.
- Changing rubberband/slinky policy.

## Likely repo scope

isaac-agent: `src/isaac/drive/turn.clj`, possibly `src/isaac/llm/tool_loop.clj` (hook). Specs: `spec/isaac/drive/turn_spec.clj` and/or `spec/isaac/llm/tool_loop_spec.clj`. Feature if needed: `features/session/` next to `context_window_guard.feature`.

## Acceptance

TDD. Exact commands once LINE numbers exist; until then the named specs in `turn_spec` / `tool_loop_spec`:

1. **Check fires mid-loop** — a grover/scripted turn with N large tool results crosses the session threshold *after* the first tool lands on disk; `check-compaction!` / compact runs *before* the second LLM request. The second `chat-fn` request's messages are the compacted view, not the full prior tool dump.
2. **In-memory request is rebuilt** — after mid-turn compact, `@current-request` / next `:messages` do not contain the pre-compact tool-result text.
3. **Under threshold: no compact** — small tools, only the turn-start check (or a mid-loop check that no-ops).
4. **Cannot save: exhaust** — compaction disabled (or no-progress) and live estimate over the 0.98 guard → `:context-exhausted`, no further LLM call.

Verify: `bb spec spec/isaac/drive/turn_spec.clj` and `bb spec spec/isaac/llm/tool_loop_spec.clj` green; `bb spec` / `bb lint` on touched files.

## Relationship

Depends on **isaac-l7lv** (completed) — mid-loop check is a no-op if the store is still the atom-flush. Complements turn-start compaction. Child of the same resilience theme as isaac-wq8m (not strictly blocked).

## Verify fail (attempt 1, 2026-08-18): touched-file lint is still red; bb lint fails on spec/isaac/llm/tool_loop_spec.clj unresolved Speclj macros
