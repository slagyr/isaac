---
# isaac-9gcs
title: A dropped provider stream (status nil, "closed") burns a hail attempt instead of deferring as weather
status: draft
type: bug
priority: normal
tags:
    - hail
    - provider-wall
created_at: 2026-09-04T00:56:30Z
updated_at: 2026-09-04T12:10:11Z
---

Observed 2026-09-04 00:55Z, isaac-work-2 (scrapper on gpt-5.4/chatgpt), hail 37ec4440 (isaac-jllj extraction, 75 minutes into the turn): `:llm/http-error :status nil :error :unknown :response-body-chars 3` on the Responses stream (772K-char request), then `:chat/stream-error :error :unknown`, `:chat/response-failed :message "closed"`, and `:hail/attempt-failed :attempts 1 :error :unknown`.

The stream simply closed — a transport failure, not a provider verdict and not poison in the bean. provider_wall classifies auth (401/403), rate limits (429/Retry-After) and usage limits as `:unavailable?` weather so the delivery worker defers (delivery_worker.clj ~366-375); a nil-status close/reset falls through as `:unknown`, which the worker treats as a real failed attempt (line ~279) — five of these dead-letter a healthy bean ([[hails-never-die]]: the dead-letter budget is for poison only).

Expected: nil-status / connection-closed / reset / timeout stream failures classify as `:unavailable? true :reason :transport` with a short retry-after (e.g. 30s, backing off), logged as `:chat/provider-transport-failure`; the delivery defers without consuming an attempt; the transcript keeps the mid-turn tool pairs so the retry resumes. Scenarios (@wip, features/session/provider_walls.feature or the hail delivery features): (1) a scripted stream that closes mid-response ends the turn :unavailable :reason :transport, not :error; (2) the hail delivery defers with the transport reason and attempts stay at 0; (3) after the retry-after the delivery rebinds and the turn continues from the persisted transcript; (4) a second consecutive transport failure backs off (retry-after doubles) but still does not burn an attempt. Related: isaac-bs5b (context-exhausted weather), isaac-xt7p (defer without burning attempts), isaac-jkx7 item 4 (tool-protocol errors as weather).



## Exhibit 2 (2026-09-04 09:55–10:55Z, tono-work-1)
The same nil-status "closed" stream drop also kills COMPACTION requests: five consecutive `:session/compaction-failed :error :unknown :message "closed"` on chatgpt tripped the auto-disable (compaction-disabled true, consecutive-failures 5 — isaac-vrtb), after which the session sat at 274,720/278,528 tokens returning `:drive/context-exhausted` every 5 minutes for tono-c76g. Recovered by hand with the opus runbook (unset flag → pin claude-opus → housekeeping turn compacted → unpin). Transport failures must not count toward the compaction auto-disable either.
