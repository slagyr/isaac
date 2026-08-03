---
name: logging
description: Use this skill when adding logging to production code or asserting on log output in tests. Covers structured logging, level discipline (info/warn/error/debug), event-keyword conventions (:domain/action), and feature-test assertions on log entries.
---

# Logging Discipline

## When This Skill Applies

Use this skill whenever you are adding log calls to production code, deciding what level a log entry deserves, or writing tests that assert on log output.

## Structured maps, not strings

Log entries must be structured maps, never raw strings. A structured map is filterable, searchable, and assertable. A raw string is none of those.

```clojure
;; GOOD — structured
(log/info {:event :session/created :session-id id :provider :anthropic})

;; BAD — raw string
(log/info (str "Session created: " id))
```

Include contextual identifiers — session, user, route, tool, provider — when available. Future-you will thank present-you when grepping logs at 2am.

## Levels

Only **info and above** are spec-worthy in feature assertions. Debug is for internal tracing; tests should not assert on debug output.

| Level | Use for |
|-------|---------|
| `info` | Lifecycle events: created, opened, started, completed |
| `warn` | Degraded-but-continuing states (override, retry, fallback) |
| `error` | Failure paths visible to the operator |
| `debug` | Internal tracing — not asserted in tests |

### Rules for new code

- Emit `info` for session lifecycle, request lifecycle, server lifecycle, long-running operations.
- Emit `error` for all failure paths visible to the operator.
- Emit `warn` for degraded-but-continuing states (e.g. config override accepted, slow path engaged).
- Emit `debug` for internal tracing; do not add feature assertions for debug events.
- Network, tool, and boundary code must log failures as structured maps.
- Every new info+ log call needs a feature scenario asserting it.

## Event keyword convention

Event names follow `:domain/action` — keyword namespace identifies the subsystem, name identifies what happened. Keep them grep-friendly.

```clojure
:session/created
:session/compaction-started
:tool/exec
:tool/execute-failed
:auth/login
:server/started
:config/set
:config/schema-registered
```

This is the single most important logging convention. It makes events discoverable (`grep ':session/'`), filterable in log pipelines, and unambiguous when multiple subsystems emit similar-sounding events.

## Feature assertion pattern

```gherkin
Then the log has entries matching:
  | level  | event                 | sessionId |
  | :info  | :session/created      | my-chat   |
  | :info  | :session/opened       | my-chat   |
```

Columns are a subset of the structured log fields; omit fields you don't care about.

Every scenario that performs a mutation or notable lifecycle event should assert its log entry. Missing log assertions let logging silently die — a regression where a mutation stops emitting `info`-level events goes unnoticed until someone is debugging in production.
