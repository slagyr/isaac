---
# isaac-v64q
title: Mid-stream 429 on the Responses path is reported as :llm-error, not weather — hail burns attempts on healthy beans (4/5 tonight)
status: todo
type: bug
priority: critical
tags:
    - hail
    - provider
    - durability
created_at: 2026-09-18T06:18:36Z
updated_at: 2026-09-18T06:18:36Z
---

## Observed (zanebot, 2026-09-18 05:50–06:20Z)

ChatGPT 429 (`:chat/stream-error :status 429 :error :rate-limited :provider "chatgpt"`, `drive/dispatch.clj:83`) hitting every worker turn. Two behaviours for the same weather:

| when the 429 lands | result shape | hail worker branch | attempts |
|---|---|---|---|
| before the stream (04:30–05:20Z) | `{:unavailable? true :reason :wall …}` | `:outcome :unavailable` → `defer-delivery!` | untouched ✓ |
| **mid-stream** (06:14Z onward; request bodies ~1.2 MB) | `{:error :llm-error :message "responses stream ended without response.completed"}` | `:outcome :error` → `reschedule!` | **burned** |

Counts at 06:20Z: `2b37a7de` (isaac-7ngj) attempts **4**, `c4684aa1` (x2lp) 3, `c63d11a2` (jkx7) 3, `2a3be661` (at5m) 3 — healthy beans one or two 429s from dead-letter. [[hails-never-die]]: dead-letter is for poison only.

## Why

`isaac.llm.api.responses` (:187): when the SSE stream stops before `response.completed` the adapter returns a generic `:llm-error` and drops the HTTP status/`retry-after` the stream error carried; `provider-wall/classify` (`wall-response?`) keys on `:status`, so the 429 is invisible to it and the drive hands hail an `:error` result. The pre-stream path keeps `:status` and is classified correctly.

## Fix

1. A stream that ends because of an HTTP error keeps `:status` (and `retry-after` / body) on the result — `{:error :rate-limited :status 429 :retry-after-ms …}` — so `provider-wall/classify` sees the wall exactly as on the non-stream path. Same for 401/403 (auth weather, [[zanebot-grok-subscription]]) and 5xx.
2. `provider-wall/normalize` is applied to stream results in the same place as non-stream results (one seam, not two).
3. Hail: `reschedule!` is never reached for a result the wall classifier would have called weather — belt and braces: if `(:error result)` is `:rate-limited` with a status in the wall set, treat as `:unavailable?`.

## Scenarios (@wip, worker writes — features/llm/provider_walls.feature has the stream fixtures)
1. a 429 arriving mid-stream on the Responses path yields `{:unavailable? true :reason :wall :retry-after-ms N}` — same assertion as the existing pre-stream scenario (`:35`).
2. a 401 mid-stream yields `:reason :auth`.
3. a genuinely truncated stream (no HTTP error) still yields `:llm-error`.
4. isaac-hail: a delivery whose turn ends `:unavailable?` from a mid-stream wall is deferred with attempts untouched (existing defer steps).

## Acceptance
```
cd isaac-agent && bb features features/llm/provider_walls.feature && bb spec spec/isaac/drive && bb ci
cd isaac-hail && bb features features/delivery.feature && bb ci
```
Field: on zanebot after the train, a 429 burst shows `:hail/delivery-deferred` and NO `:hail/attempt-failed :error :llm-error` during the burst.

## Operator note (tonight)
Any bean that dead-letters during this burst is NOT poison: `isaac hail requeue <id>` once the wall clears (ids above). Planner will requeue.
