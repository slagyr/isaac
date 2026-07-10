---
# isaac-tzgb
title: 'OIDC device poll: authorization_pending / slow_down are pending, not terminal'
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-07-10T16:45:53Z
updated_at: 2026-07-10T16:51:42Z
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

- Repo: `isaac-agent-wpny` branch `bean/isaac-tzgb`.
- `classify-oidc-poll-result` maps OAuth `error` body field; poll loop tracks mutable sleep interval for `slow_down` (+5s).

## Context

Third and hopefully last blocker on the isaac-wpny cutover: encoding (isaac-88ol) -> error surfacing (fixed with it, revealed this) -> pending classification. Micah's login is waiting on it.
