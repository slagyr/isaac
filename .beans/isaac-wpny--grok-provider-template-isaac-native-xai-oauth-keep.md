---
# isaac-wpny
title: 'grok provider template: Isaac-native xAI OAuth keeps the subscription token fresh'
status: todo
type: feature
priority: normal
created_at: 2026-07-08T16:00:30Z
updated_at: 2026-07-08T16:09:59Z
---

## Goal

A `:grok` provider template in the agent manifest gives Isaac-native xAI OAuth: `isaac auth login --provider grok` once, then Isaac keeps the SuperGrok subscription token fresh exactly the way it keeps chatgpt fresh. User config shrinks to `{:type :grok}` — mirroring the anthropic/claude and chatgpt/gpt split (xai = API-key account, grok = subscription).

## Verified findings (live probes, 2026-07-08)

- `https://auth.x.ai/.well-known/openid-configuration`: authorization `/oauth2/authorize`, token `/oauth2/token`, device `/oauth2/device/code`, verification `https://accounts.x.ai/oauth2/device`. Grant types include `device_code` and `refresh_token`.
- The Grok CLI's **public client-id `b1a00492-073a-47ea-816f-4c329264a828` is authorized for the device-code grant** (probe returned device_code + user_code, expires_in 1800, poll interval 5s).
- Access tokens live ~6h. **Refresh tokens are single-use** — every refresh response carries a rotated refresh_token that MUST be persisted before the old one is discarded.
- The token works on standard `api.x.ai/v1` — both `responses` and `chat/completions`. Subscription completes on `grok-build`, `grok-build-0.1`, and `grok-4` (served as grok-4.3); not limited to the CLI's model list.

## Design sketch

Isaac's oauth machinery is already generic (per-provider auth.json entries, single-flight refresh locking, proactive refresh window). What's hardcoded to OpenAI is parameters:

1. **Manifest**: add `:grok` to `:isaac.agent/provider-template` — `{:api "responses" :base-url "https://api.x.ai/v1" :auth "oauth-device" :oauth {:issuer "https://auth.x.ai" :client-id "b1a00492-073a-47ea-816f-4c329264a828" ...}}`. The chatgpt template gains the equivalent descriptor for OpenAI (client-id `app_EMoamEEZ73f0CkXaXp7hrann`, `https://auth.openai.com`, verification `/codex/device`) so behavior is unchanged.
2. **`isaac.llm.auth.device-code`**: parametrize client-id / endpoints (descriptor arg) instead of module-level constants.
3. **`isaac.llm.auth.store/refresh-oauth-tokens!`**: currently calls `device-code/refresh-tokens!` with OpenAI baked in — plumb the provider's oauth descriptor through. Persist the rotated refresh token (single-use!) before releasing the refresh lock.
4. **`isaac auth login --provider grok`**: provider name resolves the descriptor from the template registry.
5. **`openai/shared`**: `ChatGPT-Account-Id` and `originator` headers become chatgpt-specific (per-descriptor or provider-gated); `missing-auth-error` message stops naming OpenAI for non-chatgpt providers.

## One-time acceptance (zanebot cutover — not permanent scenarios)

- [ ] `providers/grok.edn` on zanebot becomes `{:type :grok}`; `isaac auth login --provider grok` completes; a grok-build turn runs
- [ ] Token survives its 6h expiry unattended (proactive refresh observed in logs)
- [ ] Stopgap retired: `~/bin/xai-token-sync.sh`, its crontab entry, and the `~/.grok/auth.json` mirroring are removed; Isaac holds its own token pair independent of the Grok CLI login

## Scenarios (worker writes these — required coverage)

Write gherkin scenarios covering at minimum:

1. Device-code login flow against a scripted OIDC endpoint (no real network) — descriptor-driven endpoints/client-id, tokens persisted per provider.
2. Single-use refresh rotation: the rotated refresh token is persisted before the refresh lock releases; a concurrent second refresher does not consume/clobber it.
3. chatgpt regression guard: existing chatgpt oauth behavior unchanged (its descriptor produces today's endpoints, headers, and refresh path).
4. Refresh failure classifies as a provider wall per the isaac-3tvq contract (hail defers, no dead-letter burn).

No absence tests — the stopgap-retirement items are one-time acceptance checks, not permanent scenarios.
