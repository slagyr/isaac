---
# isaac-fy7j
title: "Add crew-level :reasoning-effort override"
status: completed
type: task
priority: normal
created_at: 2026-05-05T22:42:00Z
updated_at: 2026-05-07T23:38:31Z
---

## Description

Why: isaac-ibme shipped reasoning-effort at the provider and model tiers, but the original 3-tier scope also called for crew-level overrides (:crew.<id>.reasoning-effort, taking precedence over model and provider). That tier was never implemented and ibme was scoped down to ship the OpenAI plumbing without it.

## Scope

- Wire :crew.<id>.reasoning-effort into the resolution chain so it overrides model-level and provider-level values.
- Crew-level override applies to OpenAI Responses API (and any other provider that lands the reasoning-effort feature later).
- Drop the @wip tag from features/llm/reasoning_effort.feature once the crew-level scenarios pass.

## Acceptance

- features/llm/reasoning_effort.feature has scenarios covering crew-level override precedence (crew > model > provider).
- Existing provider/model-tier scenarios continue to pass.
- @wip tag removed from the feature.

## Acceptance Criteria

crew-level :reasoning-effort overrides model and provider; feature covers the precedence chain; @wip tag dropped

