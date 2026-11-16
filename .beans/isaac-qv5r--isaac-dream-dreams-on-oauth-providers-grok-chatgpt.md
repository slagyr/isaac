---
# isaac-qv5r
title: 'isaac-dream: dreams on OAuth providers (grok, chatgpt) fail with ''Missing OAuth login'''
status: in-progress
type: bug
priority: high
tags:
    - dream
    - unverified
created_at: 2026-11-16T00:30:23Z
updated_at: 2026-11-16T00:38:35Z
parent: isaac-pcm3
---

Repo: **isaac-dream** (`src/isaac/dream/run.clj`). Bug found deploying dreaming to zanebot, 2026-11-15.

## Symptom

`isaac dream run --crew zane --since 2026-11-08` on zanebot fails at once with
`Missing OAuth login for grok. Run isaac auth login --provider grok first.`
Grok is logged in (`isaac auth status`: authenticated) and Zane's own turns on grok work fine.

## Cause

`dream-batch!` calls `dispatch/dispatch-chat` with the provider straight from
`resolve/resolve-crew-context`. That provider's config carries no `:root`, so
`isaac.agent.llm.api.openai.shared/resolve-oauth-tokens` finds no auth root and
returns nil, which reads as "missing login". Turns go through
`isaac.agent.drive.request/augment-provider`, which merges `:root` in; the dream
skips it. Every OAuth subscription provider (grok, chatgpt) is affected; API-key
and claude-code CLI providers are not. The dream scenarios only used fake
(grover) providers, so nothing caught it.

## Fix

Give the dream's provider the Isaac root the same way a turn does (reuse
`augment-provider` or the same merge; don't copy auth logic into the dream).

## Acceptance

- New scenario in isaac-dream: a crew whose dream model is on an OAuth provider
  with a stored, unexpired login dreams successfully (habit proposed, diary
  `succeeded`), and the provider request carries the stored access token.
  It fails before the fix with the "Missing OAuth login" error.
- `bb spec` and `bb features` green in isaac-dream, plus `bb jvm-spec` if the
  module has it.
- Bump the module version in its manifest.

## Work checkpoint (2026-11-15)

Done: implemented OAuth-root provider reconstruction in `src/isaac/dream/run.clj:157`, bumped manifest to 0.2.1. New Grok and ChatGPT real-provider scenarios use stored unexpired OAuth tokens and assert success, proposed habit, diary and outbound Bearer token (`features/dream/oauth.feature:22`, `spec/isaac/dream/oauth_steps.clj:21`). Confirmed both fail without the fix (exit 1) and pass with it. `bb spec`: 27 examples / 43 assertions; `bb features`: 43 examples / 159 assertions; no `bb jvm-spec` task. Implementation commit `isaac-dream` 25fb9ed pushed to `bean/isaac-qv5r`. `bb bean-gate verify isaac-qv5r` exit 2: no feature-baseline; verifier path. Next: verifier reviews `features/dream/oauth.feature:22` and lands implementation from `bean/isaac-qv5r` (ungated).
