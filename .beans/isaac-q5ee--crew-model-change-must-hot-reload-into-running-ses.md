---
# isaac-q5ee
title: Crew model change must hot-reload into running sessions (not just fresh ones)
status: in-progress
tags: [unverified]
type: bug
priority: normal
created_at: 2026-07-04T05:04:15Z
updated_at: 2026-07-04T05:43:15Z
---

## Problem (evidence, 2026-07-03)

Changing a crew's `:model` in its config does NOT apply to an already-active session's subsequent turns. On zanebot, crew `scrapper` was flipped `:sonnet -> :gpt` (config reloaded, `config/reloaded` logged), yet session `isaac-work-1` kept resolving `claude-sonnet-5` across many subsequent turns. A brand-new session (`isaac-work-2`, no prior turns) correctly resolved `gpt-5.4` on its first turn. So the model is snapshotted per active session and never refreshed on config reload.

Contrast: `:max-in-flight` (also crew config) DOES hot-reload — the delivery worker reads it from live `cfg` every tick (`crew-max-in-flight` in isaac-hail/delivery_worker.clj). The model is the outlier because it's resolved into a cached session-context/charge.

## Mechanism

- `isaac.session.context/resolve-behavior*` resolves `model-ref` from `(:model crew-cfg)` (+ session-entry model-override + defaults).
- `isaac.charge` builds the charge's `:crew-cfg` from a cached `@session-context` (charge.clj ~:141).
- That session-context (and its `crew-cfg`/model) is built once per session activation and not invalidated when the crew config file reloads, so long-lived running sessions keep the stale model until they cycle (a fresh charge from a new activation picks up the new model — which is why work-2 and earlier flips-at-cycle-boundaries worked).

## Desired behavior

A crew `:model` change (via config reload) applies to the NEXT turn of an already-active session — the model is re-resolved from live config per turn (or the cached session-context's crew-cfg is invalidated on crew-config reload), matching how `:max-in-flight` already behaves. No restart, no session cycle required.

## Scope

isaac-agent: `src/isaac/session/context.clj` (resolve-behavior*), `src/isaac/charge.clj` (@session-context caching / crew-cfg refresh). Likely the fix is to re-resolve crew-cfg-derived behavior from live config each turn, or reset the cached context on `:config/reloaded` for crew files.

## Proposed acceptance scenario (isaac-agent, needs review)

features/crew/model-reload.feature (or session/context):
- Given a crew configured with model alpha and a session on it; a turn resolves alpha.
- When the crew's config EDN is rewritten to model beta (reload).
- Then the session's NEXT turn resolves beta (not alpha).
- Plus a guard: an explicit session-level `:model` override still wins (not clobbered by crew reload).

1 new step: `the last chat request on session {s} used model {m}` (nothing asserts a turn's resolved model today).



## Acceptance scenarios (committed @wip, 2026-07-03)

isaac-agent features/crew/model_reload.feature — 2 @wip scenarios: crew model change applies to next turn of a running session; explicit session-level :model override still wins after reload. 1 new step: the-last-chat-request-on-session-used-model. Acceptance: un-@wip + bb spec/features green in isaac-agent.

## Work notes (2026-07-04)

Progress in `isaac-agent`:
- added the new step `the last chat request on session "..." used model "..."`
  in `spec/isaac/session/session_steps.clj`
- added step coverage in `spec/isaac/session/session_steps_spec.clj`
- added/adjusted `session.context` specs to document current behavior around
  model resolution across config changes and explicit session-level overrides
- un-`@wip`'d the primary crew-reload scenario in `features/crew/model_reload.feature`

Current result:
- the primary acceptance scenario passes: a running session picks up the crew
  model change on the next turn
- the explicit session-level override scenario still FAILS in the feature
  harness: a session created with explicit model `:beta` still sends requests
  using `alpha`/`echo`, so the override path in this acceptance seam is not yet
  behaving as intended

Implication:
- bean is not ready for verify handoff yet
- likely remaining work is in the session-creation / feature-harness path for
  explicit session `:model` overrides, not the crew hot-reload path itself

## Scope split (2026-07-04, prowl)

Worker found the crew hot-reload scenario passes
(`features/crew/model_reload.feature:14`, green) but the explicit
session-level override scenario (`:42`) fails because the explicit session
`:model` override is not honored at the **session-creation seam** — a session
created with model `:beta` still sends on the crew/`echo` path. That is a
distinct pre-existing defect, not part of the hot-reload path this bean owns.

Decision: **this bean is the crew hot-reload path only.** The explicit
session-level override guard is re-homed to **isaac-b3tl**.

Revised acceptance for isaac-q5ee:
- Only scenario in scope: `crew model change applies to next turn of a running
  session` (`features/crew/model_reload.feature:14`) — un-@wip, green.
- Leave the explicit-override scenario (`:42`) `@wip`; it is owned and un-@wip'd
  by isaac-b3tl.
- Keep the new step `the last chat request on session {s} used model {m}` here
  (shared; b3tl reuses it).
- `bb spec` / `bb features` green in isaac-agent with the override scenario
  still `@wip`.

Follow-up bean: **isaac-b3tl** — explicit session-level `:model` override is
ignored at the session-creation seam.


## Resolution (unverified — for verifier)

Prior commit `7c777b5` added acceptance coverage only; production still pinned
model via `charge/build` passing stale `:config` and resolved `:model` into
`resolve-behavior`. Fixed in `isaac-agent` commit `95e1cc5`.

What changed (`95e1cc5`):
- `src/isaac/charge.clj` — `behavior-opts` forwards only crew + explicit
  overrides (`:model-override`, `:model-ref`, session-entry `:model`); omits
  caller `:config` and resolved `:model` so `resolve-behavior` reads live snapshot
- `spec/isaac/charge_spec.clj` — unit test proving stale passed config still
  picks up reloaded crew model on next turn

Acceptance (unchanged from scope split):
- `features/crew/model_reload.feature:14` un-`@wip`, green
- `features/crew/model_reload.feature:42` stays `@wip` (isaac-b3tl)

Verification in `isaac-agent` on HEAD `95e1cc5`:
- `bb spec` → `1146 examples, 0 failures, 2250 assertions`
- `bb features features/crew/model_reload.feature` → `1 examples, 0 failures`
