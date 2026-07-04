---
# isaac-b3tl
title: Explicit session-level :model override is ignored at the session-creation seam
status: todo
type: bug
priority: normal
created_at: 2026-07-04T05:04:15Z
updated_at: 2026-07-04T05:04:15Z
---

## Problem (surfaced 2026-07-04 during isaac-q5ee)

A session created with an explicit session-level `:model` override does NOT send
its chat requests with that model. Evidence: a session created with explicit
model `:beta` still resolves requests along the crew/`echo` model path instead of
`beta`.

This is a **separate seam** from the crew-config hot-reload path fixed under
isaac-q5ee. q5ee proves that a crew `:model` change reloads into a running
session (scenario at `features/crew/model_reload.feature:14` — green). The
failure at `features/crew/model_reload.feature:42` is the explicit
session-override guard, and it fails because the explicit override is not honored
at session creation at all — independent of any reload. So this is a pre-existing
defect in the session-model override / session-creation seam, not a regression of
the reload work.

## Desired behavior

A session created with an explicit session-level `:model` override resolves and
sends its chat requests using that overriding model, taking precedence over the
crew-configured model — both on the first turn and after any subsequent crew
config reload (the override is never clobbered by a crew reload).

## Scope

isaac-agent: the session-creation / model-override seam.
- `src/isaac/session/context.clj` (`resolve-behavior*` precedence of
  session-entry model-override vs crew `:model`)
- session creation path that records the per-session model override into the
  session entry
- `src/isaac/charge.clj` if the override must survive on the cached
  session-context

## Acceptance scenario (re-homed from isaac-q5ee)

`isaac-agent features/crew/model_reload.feature`
- `Scenario: explicit session-level :model override still wins after reload`
  (currently at `:42`, kept `@wip` until this bean is worked)

Reuses the q5ee step `the last chat request on session {s} used model {m}`.

Definition of done:
- explicit session-level `:model` override is honored on the session's turns
- the override still wins after a crew config reload
- un-@wip the override scenario
- `clojure -M:features features/crew/model_reload.feature:42` passes
- `bb spec` / `bb features` green in isaac-agent

## Relationship

- Split out of isaac-q5ee (2026-07-04, prowl). q5ee owns the crew hot-reload
  path; this bean owns the explicit session-level override seam.
