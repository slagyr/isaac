---
# isaac-tzgb
title: 'OIDC device poll: authorization_pending / slow_down are pending, not terminal'
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-07-10T16:45:53Z
updated_at: 2026-07-10T17:11:05Z
---

## Bug

poll-for-auth! (isaac.llm.auth.device-code) only continues polling when pending-error? matches the OpenAI flow's pending idiom. Standards OIDC endpoints (auth.x.ai) signal 'not yet' with HTTP 400 + body {"error": "authorization_pending"} (RFC 8628 §3.5). The loop classifies that as terminal :api-error and exits on the FIRST poll (~5s) — before the human can possibly finish the browser step.

## Observed (2026-07-10, zanebot, twice)

isaac auth login --provider grok: device code issued correctly (isaac-88ol form-encoding fix working), then 'Error: Authorization failed: :api-error (HTTP 400): authorization_pending' after ~5 seconds.

## Fix (RFC 8628 §3.5 error handling for :flow :oidc-device-code)

- authorization_pending -> keep polling at the current interval.
- slow_down -> keep polling, interval += 5s.
- expired_token, access_denied -> terminal with the message.
- Anything else -> terminal (existing behavior).
- Classify from the OAuth error body field, not the HTTP status (pending arrives as 400).
- Respect the interval from the device-code response when present (xAI returns interval 5).

## Scenario coverage (worker)

- `device_code_spec`: authorization_pending x2 then direct `access_token`/`refresh_token` (no `authorization_code`); slow_down; access_denied; chatgpt 403 pending regression.
- `cli_spec`: grok OIDC login — pending x2 then direct token response; `exchange-tokens!` not called; `save-tokens!` gets poll tokens, exit 0. Chatgpt auth-code + exchange scenarios unchanged.

## Worker notes

- **Canonical repo:** `isaac-agent` `bean/isaac-tzgb` at **`260c31f`** (planner rescope: `login-device-code` branches on `:flow` — OIDC saves poll result, OpenAI still exchanges).
- Pending classification at `f43758d`; success-path fix in `260c31f`. `bb ci` green on `260c31f`.

## Context

Third and hopefully last blocker on the isaac-wpny cutover: encoding (isaac-88ol) -> error surfacing (fixed with it, revealed this) -> pending classification. Micah's login is waiting on it.

## Verify fail (attempt 1, 2026-07-10): branch code is green, but implementation targets an outdated integration repo that is already behind current main

Evidence:
- The worker implemented this on `isaac-agent-wpny` branch `origin/bean/isaac-tzgb` at commit `f62b4a2`.
- Focused checks are green:
  - `bb spec spec/isaac/llm/auth/device_code_spec.clj spec/isaac/llm/auth/cli_spec.clj` -> `73 examples, 0 failures, 143 assertions`
- Broader branch validation is green on that repo/branch:
  - `bb ci` -> `1186 examples, 0 failures, 2359 assertions`
  - final features pass -> `598 examples, 0 failures, 1354 assertions`
- However, the bean is now being verified after `isaac-88ol` already passed and integrated into **current** `isaac-agent` main (`origin/main` is `bc94616` / mainline has since advanced further in related work), while this bean's branch lives on the stale integration checkout `isaac-agent-wpny` and is **not based on current main**.
- I confirmed `origin/bean/isaac-tzgb` is not an ancestor of `origin/main` in `isaac-agent-wpny` (`git merge-base --is-ancestor origin/bean/isaac-tzgb origin/main` -> exit 1), so this bean cannot be fast-forward merged as-is.
- The patch itself appears mergeable by content, but verify does not implement or manually reconcile integration drift; the worker must rebase/retarget the bean onto the current canonical repo/mainline and rerun checks there.
- Passing from an outdated sibling checkout would validate the wrong integration target and risks shipping an unrebased branch.

## Verify fail (attempt 2, 2026-07-10): canonical rebase is fixed, but the OIDC success path still fails a real RFC 8628 token response

Evidence:
- Canonical branch is now correct: `isaac-agent` `origin/bean/isaac-tzgb` at `f43758d` is based on current `origin/main` `bc94616` (`git merge-base --is-ancestor origin/main origin/bean/isaac-tzgb` -> exit 0).
- Branch validation is green on that canonical repo:
  - `bb spec spec/isaac/llm/auth/device_code_spec.clj spec/isaac/llm/auth/cli_spec.clj` -> `69 examples, 0 failures, 136 assertions`
  - `bb ci` -> `1208 examples, 0 failures, 2405 assertions`
  - final features pass -> `621 examples, 0 failures, 1425 assertions`
- The pending classification fix itself is present in `src/isaac/llm/auth/device_code.clj`: `classify-oidc-poll-result` now treats `authorization_pending` / `slow_down` as pending and increases the interval for `slow_down`.
- But `isaac.llm.auth.cli/login-device-code` still assumes a successful OIDC poll returns `:authorization_code` + `:code_verifier` and unconditionally calls `exchange-tokens!` with those fields (`src/isaac/llm/auth/cli.clj:84-86`).
- `https://auth.x.ai/.well-known/openid-configuration` advertises a standard device-code token endpoint (`https://auth.x.ai/oauth2/token`) with grant type `urn:ietf:params:oauth:grant-type:device_code`; RFC 8628 §3.5 says successful polling returns the token response directly.
- I independently reproduced the mismatch by stubbing the Grok login path so `poll-for-auth!` returns a successful token payload `{:access_token "at" :refresh_token "rt" :expires_in 3600}`. The CLI returned `rc 1`, called `exchange-tokens!` with `{:authorization-code nil, :code-verifier nil}`, and printed `Error: Token exchange failed: :api-error`.
- The new tests stay green because they model OIDC success as `{:authorization_code ... :code_verifier ...}` in both `device_code_spec.clj` and `cli_spec.clj`, so they do not cover the real standards-compliant success shape.

## Planner rescope (2026-07-10, prowl) — the OIDC success path is IN scope

The verifier is correct and this is not scope creep: the whole point of the bean
is that Micah's grok login works end-to-end. Fixing pending-classification while
the success path still errors leaves the login broken. Fold the success contract
into this bean.

### Root cause (confirmed on `origin/bean/isaac-tzgb` at `f43758d`)

Two device-code flows, two DIFFERENT success shapes:

- `:openai-device-auth` (chatgpt): poll success returns an **authorization
  code**; a SEPARATE `exchange-tokens!` call swaps it for tokens. (Existing
  behavior — keep it.)
- `:oidc-device-code` (grok / xAI): `poll-url` resolves to `:token-path`
  (`/oauth2/token`) and polls with
  `grant_type=urn:ietf:params:oauth:grant-type:device_code`. Per **RFC 8628
  §3.5**, a successful poll returns the **token response directly**
  (`access_token` / `refresh_token` / `token_type` / `expires_in`). There is NO
  `authorization_code`, NO `code_verifier`, and NO second exchange.

`isaac.llm.auth.cli/login-device-code` (`cli.clj:~84-86`) is flow-blind: on any
non-error poll result it unconditionally calls `exchange-tokens!` with
`(:authorization_code auth-resp)` / `(:code_verifier auth-resp)`. For OIDC those
keys are absent → `exchange-tokens!` posts nils → `:api-error`. The login can
never succeed against a standards-compliant OIDC provider.

### Required fix (make the success path flow-aware)

- **OIDC (`:oidc-device-code`)**: a successful `poll-for-auth!` result IS the
  token map. Save it directly via `auth-store/save-tokens!` — do NOT call
  `exchange-tokens!`.
- **OpenAI (`:openai-device-auth`)**: unchanged — poll returns the auth code,
  then `exchange-tokens!`, then save.
- Branch on the descriptor's `:flow` (or an equivalent explicit predicate) in
  `login-device-code`; do not infer from the presence/absence of keys.
- A token map must be recognized by its actual OIDC fields (`access_token`),
  not by `authorization_code`.

### Scenario corrections (the current tests model the WRONG success shape)

- `cli_spec.clj`: the grok/OIDC login scenario must drive
  `authorization_pending` ×2 then a **direct token response**
  (`{:access_token ... :refresh_token ... :expires_in ...}`), and assert:
  1. `exchange-tokens!` is **NOT** called on the OIDC path, and
  2. tokens are persisted from the poll result, exit 0.
- `device_code_spec.clj`: OIDC `poll-for-auth!` success returns the token map
  unchanged (already true) — add/adjust a case that asserts the token fields
  survive classification, not an `authorization_code`.
- Keep the chatgpt/OpenAI auth-code→exchange scenarios exactly as-is
  (regression guard that the OpenAI flow is untouched).

### Acceptance addendum

- [ ] Grok OIDC login succeeds when the poll returns a direct RFC 8628 token
      response; `exchange-tokens!` is not invoked on the OIDC path.
- [ ] OpenAI/chatgpt auth-code + exchange path still green (regression).
- [ ] `bb ci` green on the canonical `isaac-agent` branch (rebased on current
      `origin/main`).

### Fail-count reset

This planner note resets the verify-fail count. Resume in work.
