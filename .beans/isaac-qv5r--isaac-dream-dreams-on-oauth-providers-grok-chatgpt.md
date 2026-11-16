---
# isaac-qv5r
title: 'isaac-dream: dreams on OAuth providers (grok, chatgpt) fail with ''Missing OAuth login'''
status: in-progress
type: bug
priority: high
tags:
    - dream
created_at: 2026-11-16T00:30:23Z
updated_at: 2026-11-16T00:31:22Z
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

Done: claimed bean; added OAuth dream scenarios for Grok and ChatGPT, stored OAuth login fixture, token assertion, root-augmented provider in `src/isaac/dream/run.clj`, bumped manifest to 0.2.1. First Grover simulation test passed after fix and failed without fix but simulated OAuth bypasses missing-login check; replaced with real-provider scenarios and stubbed transport. Last test `bb features features/dream/oauth.feature` RED due to temporary diagnostic assertion (`error diagnostic`) despite diary error nil (success). Next: remove diagnostic rows in `features/dream/oauth.feature:32` and `:54`, verify real-provider test fails without root and succeeds with root; run `bb spec`, `bb features`, gate, hand off ungated bean.
