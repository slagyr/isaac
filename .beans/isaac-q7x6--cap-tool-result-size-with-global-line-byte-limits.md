---
# isaac-q7x6
title: Cap tool result size with global line + byte limits before transcript persist
status: draft
type: feature
priority: normal
created_at: 2026-05-16T17:22:40Z
updated_at: 2026-05-16T17:50:30Z
---

## Problem

Tool results currently land in the transcript without a uniform cap:
- `exec` (`src/isaac/tool/exec.clj:34`) does `(slurp ...)` of full stdout/stderr — unbounded
- `read` caps at 2000 lines (`*default-read-limit*`) but one minified-JS or JSON line can still be megabytes
- `web_fetch`, `grep`, `glob` have line-based caps but no byte safety net

Prompt-build truncates again on the way to the LLM (`transcript/truncate-tool-result`, head/tail at 30% of context × 4), but the **on-disk** transcript keeps whatever the tool returned. Net effect: pagination, log dumps, or one bad exec inflate the transcript permanently.

## Approach

Single global cap applied in `run-tool-calls!` (`src/isaac/drive/turn.clj:148`) — or a shared truncator helper — before the `toolResult` message is persisted. Two bounds, either trips first:
- `:max-output-lines` (default `2000`, matches current `read` cap)
- `:max-output-bytes` (default `262144` / 256KB)

Head-and-tail truncation, marker reports total size AND which bound tripped, e.g.:
```
... [524288 bytes truncated; byte cap hit] ...
```

Reuse existing `transcript/truncate-tool-result`'s head-tail strategy; promote it to a shared `tool-bounds` namespace so it's the canonical truncator.

## Config placement

```clojure
{:tools {:defaults {:max-output-lines 2000
                    :max-output-bytes 262144}}}
```

Cascade: installation default → crew override. No per-tool override in v1 (defer per `feedback_planner_role` if needed).

## Scope

- Replace ad-hoc caps in `read`, `web_fetch`, `grep`, `glob` so all tools route through the single truncator
- `exec` gains the cap it currently lacks
- Marker shape unified across tools

## Out of scope

- Per-tool override
- Storing the full result anywhere (it's truly truncated, not sidecar'd)

## Feature file

`features/tools/output_cap.feature` — three @wip scenarios:

- exec stdout exceeding the byte cap is truncated with a marker naming the cap
- read of a file with many short lines is truncated with a marker naming the line cap
- the truncated tool result is what gets persisted to the transcript

## Acceptance

```
bb features features/tools/output_cap.feature
```

All three scenarios pass; remove `@wip`.
