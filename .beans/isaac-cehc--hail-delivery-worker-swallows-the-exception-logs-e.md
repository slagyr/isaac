---
# isaac-cehc
title: Hail delivery worker swallows the exception (logs :error :exception, not class/message)
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-07-05T16:23:33Z
updated_at: 2026-07-06T14:31:58Z
---

## Problem

The hail delivery worker catches a delivery/turn exception and logs it as `:error :exception` — a bare keyword — discarding the exception's message, class, and stack. When a delivery fails, the operator sees `:event :hail/attempt-failed :error :exception` with no indication of WHAT failed. The exception is effectively swallowed.

## Evidence (2026-07-05, zanebot)

isaac-4tn1 dead-lettered repeatedly (attempts 1..5) on isaac-work-1. Every log line said only `:error :exception`. The actual cause — a `com.fasterxml.jackson.core.JsonParseException` from a mid-line history offset — was invisible in the logs. It could only be found by manually reproducing the turn via `isaac prompt --session isaac-work-1`, which surfaced the full stack. This hid a session-bricking bug (isaac-63f3) for hours.

## Desired behavior

When a delivery attempt throws, log the exception's class and message (and ideally a truncated stack / ex-data) at WARN on attempt-failed and at ERROR on dead-letter — not a bare `:exception` keyword. An operator should be able to read the log and know the failure cause without reproducing it.

Suggested fields: `:ex-class`, `:ex-message`, and (debug level) `:stack` or the first N frames.

## Scope

isaac-hail: `src/isaac/hail/delivery_worker.clj` — `reschedule!` / `dead-letter!` and wherever the delivery attempt's exception is caught and reduced to `:error :exception`. Make the caught throwable's class+message part of the log event.

## Acceptance (gherkin, isaac-hail)

- Given a delivery attempt that throws with a known message, when it fails, then the `hail/attempt-failed` (and `hail/dead-lettered`) log event includes the exception class and message (not a bare `:exception` keyword).

Priority: HIGH — observability; this swallowing turned a findable bug into a multi-hour hunt.


## Design (approved 2026-07-05)

The catch at delivery_worker.clj:377-379 captures `.getMessage e` into a result map, but line 369 passes only `(:error result)` (the keyword `:exception`) to reschedule!, discarding the message before it reaches the `:hail/attempt-failed` (276) and `:hail/dead-lettered` (245) log events. Fix: capture `:ex-class` (+ existing `:ex-message`) at the catch, thread the full error info through reschedule!/dead-letter!, and log `:ex-class`/`:ex-message` — never a bare `:error :exception`.

## Acceptance (isaac-hail; reuse jnkp/ic4g :hail/* log-event assertions)

1. Given a delivery whose turn throws with message "boom-xyz", when the worker attempts it, then the `:hail/attempt-failed` log event includes ex-class and ex-message "boom-xyz" (not a bare :exception keyword).
2. Given a delivery that throws on every attempt, when it exhausts retries, then the `:hail/dead-lettered` log event includes ex-class and ex-message.

New steps: a fixture "a delivery whose turn throws with message X" (~1), plus a log-event field assertion likely extending the jnkp/ic4g machinery. Definition of done: both scenarios green; bb spec/features green in isaac-hail.

## Scope

isaac-hail: src/isaac/hail/delivery_worker.clj (catch ~377, reschedule! ~276, dead-letter! ~245); features under features/ reusing hail lifecycle log assertions.
