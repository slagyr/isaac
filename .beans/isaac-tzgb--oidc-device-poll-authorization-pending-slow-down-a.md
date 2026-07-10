---
# isaac-tzgb
title: 'OIDC device poll: authorization_pending / slow_down are pending, not terminal'
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-07-10T16:45:53Z
updated_at: 2026-07-10T16:53:21Z
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

- `device_code_spec`: authorization_pending x2 then success; slow_down sleeps 5s then 10s; access_denied terminal; chatgpt 403 pending regression unchanged.
- `cli_spec`: full grok login through `-post-form!` stub with authorization_pending x2 then auth code (proves poll loop in login path).

## Worker notes

- **Canonical repo:** `isaac-agent` branch `bean/isaac-tzgb` at `f43758d` (rebased on `origin/main` `bc94616` after verify-fail; replaces stale `isaac-agent-wpny` `f62b4a2`).
- `classify-oidc-poll-result` maps OAuth `error` body field; poll loop tracks mutable sleep interval for `slow_down` (+5s).
- Re-verify: `bb ci` green on `isaac-agent` at `f43758d` (1212 specs + 621 features).

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
