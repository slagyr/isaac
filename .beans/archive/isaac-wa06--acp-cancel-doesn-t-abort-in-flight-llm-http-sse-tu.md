---
# isaac-wa06
title: "ACP cancel doesn't abort in-flight LLM HTTP/SSE; turn finishes naturally"
status: completed
type: bug
priority: normal
created_at: 2026-04-29T18:19:54Z
updated_at: 2026-04-29T21:36:40Z
---

## Description

ACP session/cancel sets the cancelled? flag and fires hooks, but no
hook closes the in-flight LLM HTTP/SSE connection. The model keeps
generating until completion; the turn only ends after the natural
response arrives. User-visible: clicking cancel does nothing for
the duration of the response.

## Current flow (working as designed but incomplete)

1. ACP session/cancel -> session-cancel-handler in
   src/isaac/acp/server.clj:109
2. -> bridge/cancel! sets (:cancelled? turn) true and runs hooks
3. Tool calls register hooks (turn.clj:551) so exec-tool polls the
   flag and kills the process - that part works.
4. The LLM call itself registers no hook. post-sse! keeps reading
   the stream.
5. After the LLM finishes, turn.clj:565-ish checks cancelled? and
   aborts: the turn ends "cancelled" but all the content was
   already generated and billed.

## Fix shape

- src/isaac/llm/http.clj post-sse! gains a cancellation handle
  (return a {:close! fn} alongside the result, OR accept an
  AbortSignal-style atom that closes the HTTP request when set)
- src/isaac/drive/turn.clj registers (bridge/on-cancel! key-str
  #(close! handle)) before invoking the SSE dispatch, so cancel
  closes the connection and the loop unwinds promptly.
- The dispatch-chat-stream and dispatch-chat-with-tools paths both
  need this.

## Spec

Behavioral feature: a turn cancelled mid-LLM-call ends within ~1s
without consuming the rest of the stream. Easiest to test if grover
supports a "slow stream" response shape (e.g. a vector of fragments
with a per-fragment delay) — cancel mid-stream should produce a
cancelled result before the queue drains. If that's too much
infrastructure, a unit spec asserting that bridge/cancel! invokes
the close-handle on a stubbed LLM stream is sufficient.

Add @wip scenario to features/acp/cancel.feature (or similar) plus
a unit spec.

## Related

- isaac-juhh (unify streaming and tools paths) — cancellation
  becomes more important once tool-using turns stream too. Land
  this either with juhh or right after.

## Definition of done

- ACP cancel during a long LLM response causes the turn to end
  promptly (sub-second) with :cancelled status.
- Stream is actually closed (no further deltas processed after
  cancel).
- bb features and bb spec green.

## Notes

Added active stream cancellation for llm/http streaming calls by registering bridge cancel hooks that close live SSE/NDJSON bodies. post-sse! and post-ndjson-stream! now return {:error :cancelled} promptly when a session is cancelled mid-stream instead of waiting for natural completion. Added a unit spec in spec/isaac/llm/http_spec.clj covering mid-stream cancellation and verified features/acp/cancel.feature passes. Full bb spec passes. Full bb features now has one unrelated failure in features/cli/acp.feature (no-model crew resolution), outside this bead's scope and tracked separately in isaac-gztb.

