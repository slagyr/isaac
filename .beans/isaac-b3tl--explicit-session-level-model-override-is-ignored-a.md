---
# isaac-b3tl
title: Explicit session-level :model override is ignored at the session-creation seam
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-07-04T05:04:15Z
updated_at: 2026-07-06T14:28:22Z
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


## Work notes (2026-07-04)

Initial verification of the split-off seam in `isaac-agent`:
- the shared step `the last chat request on session "..." used model "..."` is already present in `spec/isaac/session/session_steps.clj` and remains available for this bean
- the scenario remains `@wip` in `features/crew/model_reload.feature` pending implementation
- `bb spec` is green: `1138 examples, 0 failures, 2237 assertions`
- `bb features` is green for the scoped q5ee state with only the hot-reload scenario active: `573 examples, 0 failures, 1284 assertions`

No new product change has been made for `isaac-b3tl` yet in this turn; this note records the starting state after the planner scope split.

---

## Resolution (unverified — for verifier)

isaac-agent `main` commit **113b633**:

**Root cause.** Session `:model` refs like `":beta"` were stored/resolved without
canonical id coercion (`->id`), so config lookup missed `models` entries. Feature
turns also passed a resolved `:model` on the dispatch request, bypassing
`charge/build`'s live `resolve-behavior` path (same stale-pin class as q5ee).

**Fix.**
- `normalize-model-ref` in `session/context.clj` — coerce keyword-ish refs to
  canonical ids at create + `resolve-behavior*`.
- `charge/behavior-opts` — forward normalized session `:model` to resolve-behavior.
- Feature harness — parse `model` gherkin values as refs; lookup via
  `normalize-model-ref`; omit `:model` from `user-sends` dispatch opts.

**Tests.** `@wip` removed; `model_reload.feature` override scenario green (crew
`flipper` + session `:beta` override survives crew reload). New
`context_spec` covers override precedence + post-reload stability.

**Verification:** `bb verify` — 1168 spec / 581 feature examples, 0 failures;
`model_reload.feature` green.
