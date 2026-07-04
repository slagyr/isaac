---
# isaac-q5ee
title: Crew model change must hot-reload into running sessions (not just fresh ones)
status: in-progress
type: bug
priority: normal
created_at: 2026-07-04T05:04:15Z
updated_at: 2026-07-04T05:10:14Z
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
