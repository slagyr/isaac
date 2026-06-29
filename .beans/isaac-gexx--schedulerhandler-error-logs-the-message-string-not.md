---
# isaac-gexx
title: scheduler/handler-error logs the message string, not the throwable (no stacktrace)
status: completed
type: bug
priority: normal
tags: []
created_at: 2026-06-29T14:50:59Z
updated_at: 2026-06-29T18:00:00Z
---

scheduler/runtime.clj ~207-209 builds the error note with :error-msg (.getMessage ^Exception error) and line 294 logs :error error-msg. Only the exception MESSAGE is kept; the throwable (and its stacktrace) is discarded. So scheduler/handler-error log entries can never carry a stacktrace, unlike :ws/error which logs :throwable #error{...}. Makes scheduler failures hard to diagnose (e.g. the 'Output closed' loop gives no origin).

## Fix
Carry the throwable through the error-note and log it as :throwable (structured #error) like the ws path, in addition to (or instead of) the message string.

## Couples with isaac-x2po (one-line invariant)
When adding :throwable to scheduler errors, serialize it SINGLE-LINE (escape newlines), per the one-physical-line-per-entry invariant in isaac-x2po. Do NOT pretty-print the #error across lines or it reintroduces the viewer breakage. Land with x2po.

## Implementation (work-2, 2026-06-29)
Repo: **isaac-foundation**

- Implementation commit: `fc845038879dbe0ce86e91ccd49104b8819f85bb`
- Current `origin/main`: `5b7e87cc0b6ec2c8d7fc9f2153f59abeb8d215fc` (includes gexx + x2po writer normalization)

Changes:
- `logger/single-line-throwable` — map with `:class`, `:message`, `:stacktrace` (newline-escaped)
- `scheduler/runtime` carries `:throwable` in handler-error notes; logs `:throwable` + `:error` message

Verification: `bb spec spec/isaac/logger_spec.clj spec/isaac/scheduler_spec.clj` → 67 examples, 0 failures

## Verification failed (2026-06-29, stale fetch)
Verifier reported `origin/main` at `c230d54` without `fc84503`. Confirmed via `git ls-remote origin main` that `main` is now `5b7e87c` and `fc84503` is an ancestor; `runtime.clj:294` logs `:throwable (log/single-line-throwable throwable)`.

## Verification (2026-06-29)
Verified on fetched GitHub `isaac-foundation` `main` `5b7e87cc0b6ec2c8d7fc9f2153f59abeb8d215fc`, where `fc84503` is present under the current head. Focused proof passed:

- `bb spec spec/isaac/logger_spec.clj spec/isaac/scheduler_spec.clj` -> `67 examples, 0 failures, 114 assertions`
