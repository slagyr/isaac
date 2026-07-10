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

- Repo: `isaac-agent-wpny` branch `bean/isaac-tzgb`.
- `classify-oidc-poll-result` maps OAuth `error` body field; poll loop tracks mutable sleep interval for `slow_down` (+5s).

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
