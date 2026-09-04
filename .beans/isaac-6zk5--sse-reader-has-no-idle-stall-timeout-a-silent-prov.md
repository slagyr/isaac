---
# isaac-6zk5
title: 'SSE reader has no idle-stall timeout: a silent provider stream blocks the turn until the socket dies (~15 min)'
status: todo
type: bug
priority: high
tags:
    - agent
    - provider-weather
created_at: 2026-09-04T16:29:12Z
updated_at: 2026-09-04T16:29:12Z
---

Repo: **isaac-agent** (`src/isaac/llm/http.clj` `post-sse!` / `cancellable-call`).

## Problem

`post-sse!` opens the provider stream (`:as :stream`, `:timeout 120000` covers
the request) and then reads it with `line-seq` inside `process-sse-lines`.
Nothing bounds the wait BETWEEN chunks: a stream that goes silent blocks the
reader until the far end closes the socket. Observed on zanebot 2026-09-03
(five times) and 2026-09-04 (twice): chatgpt compaction requests produce no
bytes, the connection is torn down after ~15 min, and the failure surfaces as
`:session/compaction-failed :message "closed"`. Each stall costs a full
15 minutes of worker wall clock; the thread dump during the 16:00Z stall shows
the turn parked in `process-sse-lines` → `cancellable-call` deref. `lsof`
showed no ESTABLISHED connection to the provider at the time, i.e. the
socket was already gone and the reader had not noticed.

## Fix

- Idle-stall timeout on the SSE read: if no bytes/events arrive for
  `:stream-idle-timeout-ms` (default 90s; config under `:defaults`, per-
  provider override), close the stream and return
  `{:error :stream-stalled :unavailable? true :retry-after-ms …}` — classified
  as provider weather (retryable, does not burn a hail attempt), never a
  silent 15-minute wait. Log `:llm/stream-stalled` with elapsed ms and the
  bytes received so far.
- Implement in `cancellable-call`'s polling loop (it already wakes every
  50 ms to check cancellation) by tracking a last-activity timestamp updated
  from `on-chunk`, so no extra thread is needed.
- Same guard for the non-streaming `post!` path if it can block on body read.

## Acceptance

`spec/isaac/llm/http_spec.clj`: a fake stream that emits two events then
goes silent → with idle timeout 100 ms the call returns `:stream-stalled`
within ~200 ms, the partial events were delivered to `on-chunk`, and the
stream was closed; a stream that keeps emitting slower than the timeout is
NOT cut off (timer resets per chunk). `provider_wall`/drive classification
treats `:stream-stalled` as unavailable-weather (spec in
`spec/isaac/drive/provider_wall_spec.clj`). Hail delivery worker defers on it
without incrementing attempts (existing weather path; add one example).
`bb spec spec/isaac/llm spec/isaac/drive` 0 failures; full gate green.
