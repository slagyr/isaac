---
# isaac-twbz
title: 'ACP prompt: ~6s of local prep before LLM request'
status: todo
type: task
priority: high
created_at: 2026-06-20T23:55:35Z
updated_at: 2026-09-18T05:04:27Z
---

On zanebot (isaac 0.1.6), there is ~5.7s of local work between receiving an ACP `session/prompt` frame and the outbound `llm/http-request`. Observed in /tmp/isaac.log for session "tidy-comet"; the model response itself comes back faster (~3.5s) than the prep that precedes it. Same on every prompt.

## Timeline (frame -> http-request, ~5.72s)
From two consecutive prompts (23:38:04 and 23:39:03), nearly identical:

| delta | stage | event |
|-------|-------|-------|
| t0       | frame-received        | acp-ws/frame-received (session/prompt) |
| +0.60s   | config re-resolved    | config/set-snapshot "ACP session/prompt effective config" |
| +1.27s   | behavior              | session/behavior-resolved |
| +1.14s   | catalog               | prompt/catalog-resolved (elapsed-ms 0.3 — compute is instant; the gap is I/O/assembly) |
| +0.55s   | token accounting      | session/compaction-check |
| +2.05s   | serialize request     | turn/request-built (422 messages) |
| +0.10s   | network               | llm/http-request (body-chars 1,112,829 = 1.1 MB) |

## Root cause 1 — over context window, not compacting (biggest + a correctness smell)
- compaction-check: total-tokens 310635 vs context-window 278528 — ~32k OVER the limit, yet no compaction fires.
- Every turn ships 422 messages / 1.1 MB JSON. Response shows cached-tokens 310272 (server-side cached), but isaac still builds+serializes the full 1.1 MB locally each turn -> the ~2s turn/request-built step, plus wasted tokens.
- Investigate the compaction threshold logic in isaac.agent drive/turn.clj (~line 521) — why isn't it triggering at 310k > 278k?

## Root cause 2 — per-turn re-resolution (~3.5s of local CPU/IO)
- config snapshot, behavior, prompt/soul catalog, and tool berths (berth/registered for all 16 tools fires inside the turn) are all rebuilt from scratch on every session/prompt.
- For a stable long-lived session these could be cached/memoized across turns.

## Suggested fix order
1. Fix compaction so the session stays under the window -> shrinks the body, cuts the ~2s serialize, stops overpaying tokens.
2. Cache per-turn config/behavior/catalog resolution within a session -> reclaims most of the remaining ~3s.

## Environment
- Host: zanebot, isaac 0.1.6 (Homebrew Cellar)
- Provider: chatgpt, model gpt-5.4, context-window 278528
- Source refs seen in log: isaac.agent @ 9ba425d (drive/turn.clj, session/context.clj, prompt/catalog.clj), isaac.comm.acp @ d986755 (websocket.clj)


## Re-scoped (planner, 2026-09-18): measure first

The July numbers predate isaac-v1la (config resolved once), isaac-3uy9 (compaction check no longer materializes messages; prompt build timed), isaac-h8o9 (per-step compaction timings) and isaac-gwdz (store reads 1.4 s → ms, deployed 2026-09-18). Root cause 1 (over-window, no compaction) may be gone; root cause 2 (per-prompt re-resolution of config/behavior/catalog/tool berths) is unmeasured on current code. Do NOT implement from the July table.

## Task
1. On zanebot (agent 0.1.71+), drive three consecutive ACP prompts on one long-lived session (`isaac remote … -- acp` or the editor) and rebuild the table frame-received → llm/http-request from cli.log/server.log with the events that now exist (`config/set-snapshot`, `session/behavior-resolved`, `prompt/catalog-resolved`, `session/compaction-check` with its step fields, `turn/request-built`, `llm/http-request`). Record it here.
2. Any stage ≥ 300 ms gets its own bean with the number attached (candidates: config snapshot re-resolution per prompt — should be served from the hot-reloaded live config now; catalog/behavior memoization per session; request serialization size).
3. If total prep is under ~1 s, close this bean with the table.

## Acceptance
The re-measured table on this bean (three prompts, same session), each stage named, and either follow-up bean ids or a closing note. No code change in this bean.
