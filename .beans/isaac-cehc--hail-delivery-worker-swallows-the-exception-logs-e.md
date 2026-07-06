---
# isaac-cehc
title: Hail delivery worker swallows the exception (logs :error :exception, not class/message)
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-07-05T16:23:33Z
updated_at: 2026-07-06T15:01:00Z
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


## Verification failed

HEAD: 3ac1f999c343b675f4db9be2a4886f5c1e6bcd91
Working tree: clean

The gherkin acceptance scenarios added for isaac-cehc in features/delivery.feature are not green (3 failures in delivery.feature run).

- New cehc scenarios fail with error: :no-model , ex-message: nil (instead of :exception / boom-xyz). The 'a delivery whose turn throws' stub is not reached; charge/unresolved? returns :no-model for the 'grover' setup in the scenario before hitting run-turn! stub.

- Other pre-existing failures in delivery.feature (bound delivery dispatch, serializing ticks) also present in this tree.

Unit spec (delivery_worker_spec) is green (22 ex, 0 fail) and directly tests the log events with ex-class/ex-message.

Code in delivery_worker.clj matches the Design (exception-error-info with :ex-class, threads err map with ex-class/ex-message to reschedule/dead-letter, logs include them).

However, the bean's Acceptance requires the gherkin scenarios green. Bean body still lacks a 'Resolution (unverified — for verifier)' section documenting the implementation commit (cee16f2 in hail).

Feature edit: only additions (new scenarios + step), no removal/weaken of existing.

Recommend: fix the test setup in the new scenarios (ensure model/crew config makes charge resolve so the throw stub is exercised) or adjust the fixture. Re-run features/delivery.feature until the two cehc scenarios pass.

## Resolution (unverified — for verifier)

Implemented in `isaac-hail` commits **cee16f2** (production fix) and **6cb529f** (feature harness fix).

### Production (`cee16f2`)

- `src/isaac/hail/delivery_worker.clj` — `exception-error-info` captures `:ex-class`/`:ex-message` at the catch; `failure-log-context` threads the error map through `reschedule!`/`dead-letter!`; `:hail/attempt-failed` and `:hail/dead-lettered` log those fields (not a bare `:error :exception`).
- `spec/isaac/hail/delivery_worker_spec.clj` — unit coverage for thrown-turn attempt-failed and dead-lettered log shapes.

### Feature harness (`6cb529f`)

Verifier failure was `:no-model` before the throw stub — charge never reached `run-turn!`. Fixed by mirroring the jnkp scenario setup:

- `features/delivery.feature` — both cehc scenarios now lead with `default Grover setup`, queue a grover response, then apply the throw stub; log tables assert `:ex-class` + `:ex-message`.
- `feature-steps/isaac/hail_steps.clj` — `a delivery whose turn throws with message X` installs grover fixture + var-root `run-turn!` stub (async-safe); after-scenario restores.

### Verified

- `bb spec` — 122 examples, 0 failures.
- `clojure -A:dev-local:features -M:features features/delivery.feature` — both cehc scenarios green; two pre-existing flaky scenarios in the same file (`bound delivery dispatches`, `serializing ticks`) still fail intermittently on this tree (present before cehc).
