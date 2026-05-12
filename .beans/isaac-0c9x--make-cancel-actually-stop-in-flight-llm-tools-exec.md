---
# isaac-0c9x
title: Make cancel actually stop in-flight LLM, tools, exec, and slash work
status: draft
type: feature
priority: normal
created_at: 2026-05-12T22:55:39Z
updated_at: 2026-05-12T22:56:11Z
blocked_by:
    - isaac-yr1x
---

## Problem

Pressing ESC in Toad mid-turn currently sets the `bridge/cancel!` flag,
but nothing in the turn pipeline reads it until the *whole tool-loop
returns*. Marvin's recent long turn ignored ESC for minutes because:

1. `src/isaac/llm/tool_loop.clj` has no cancellation check between
   chat/tool cycles.
2. `record-tool-call!` (`drive/turn.clj:638`) registers an on-cancel hook
   that only emits `comm/on-tool-cancel` — it does NOT interrupt the
   running tool.
3. `chat-fn` / SSE streaming never aborts mid-response. We always read
   to completion.
4. `isaac.tool.exec` doesn't kill its subprocess on cancel.
5. `web_fetch` / `web_search` don't abort their HTTP requests.
6. Slash command handlers run synchronously with no opportunity to
   check `bridge/cancelled?`.

## Proposed scope

### 1. Tool-loop cancellation (cheapest, biggest win)

`tool-loop/run` accepts a `:cancelled?` option `(fn [] -> bool)`. At the
top of each iteration (after `chat-fn` returns, before invoking
`tool-fn` for the batch), check it; if cancelled, return
`{:response last-response :tool-calls all-tools :token-counts ...
:cancelled? true}` and stop.

`execute-llm-turn!` passes `#(bridge/cancelled? session-key)` in. The
existing cond at `turn.clj:689-693` already maps `:cancelled?` →
`bridge/cancelled-result`.

### 2. Per-tool cancellation hooks

`record-tool-call!` passes a `cancelled?` predicate into the tool's
arguments (or via a thread-local), so cooperative tools can check it.
Adapt the long-running tools:

- **`isaac.tool.exec`**: hold the spawned `Process` in an atom;
  register an `on-cancel!` hook that calls `.destroyForcibly()`.
- **`isaac.tool.web-fetch` / `isaac.tool.web-search`**: use
  http-kit/clj-http with cancellable async/deferred; close the
  response on cancel.
- File/grep/glob/memory: fast enough — no in-tool cancel needed beyond
  the tool-loop pre-check above.

### 3. LLM SSE stream abort

`llm-http/post-sse!` plumbs a `cancelled?` token. The SSE read loop
breaks out when the token flips; the underlying http-kit connection
is closed, which cascades to the upstream LLM provider. Wire this
through `chat-stream` for each provider's `Api` implementation.

### 4. Slash command hooks

Slash handlers should be able to check `bridge/cancelled?` and bail.
Audit built-in slash handlers — most are sync and trivial, but
`module`-contributed slash commands could be long-running. Document
the contract in `isaac.slash.registry`.

### 5. Async compaction is already lifecycle-managed

Out of scope — has its own future/lock and isn't turn-bound.

## Acceptance scenarios (TBD)

This bean is `draft` until concrete scenarios exist. Candidates:

- Scenario A: turn is mid-tool-loop; cancel fires; loop exits within
  one cycle without invoking the next tool batch.
- Scenario B: `exec` tool is running a long-running subprocess; cancel
  fires; process is destroyed and tool returns within 100ms.
- Scenario C: chat SSE stream is mid-response; cancel fires; HTTP
  connection closes and turn returns `:cancelled` within 200ms.
- Scenario D: web_fetch is in flight to a slow endpoint; cancel
  aborts the HTTP request.

Write these as feature scenarios (or specs) before promoting to todo.

## Depends on

- Observability bean (isaac-plan-XXXX — sibling) should land first so
  we can confirm Toad's ESC is reaching the server.
