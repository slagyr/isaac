---
# isaac-blt2
title: Grok OAuth asks for grok-cli:access so the quota meter can read billing
status: completed
type: task
priority: high
tags:
    - agent
created_at: 2026-10-28T08:25:40Z
updated_at: 2026-10-28T08:38:05Z
---

## Why

The Grok quota meter (isaac-7q4c) calls `cli-chat-proxy.grok.com/v1/billing`. With Isaac's Grok login it gets `403 {"error":"User does not have Grok Code CLI permission"}` (skiff, 2026-10-28). Isaac's device login asks only for `api:access offline_access`; the grok CLI — same xAI OAuth client id — asks for `openid profile email offline_access grok-cli:access api:access`, and its token reads billing fine.

## What

Grok's OAuth scope becomes `openid profile email offline_access grok-cli:access api:access` — in **both** places it lives today: `isaac.agent.llm.auth.device-code/grok-descriptor` and the `:grok` provider template's `:oauth :scope` in `resources/isaac-manifest.edn`. One source if you can make it one.

Existing logins keep their old scope until someone logs in again (a refresh cannot widen scope) — that is a one-time human step after deploy, not part of this bean. Nice to have: when the Grok meter gets this 403, its `:unknown` error says to log in again (`isaac auth login --provider grok`).

## Acceptance

- Scenario: isaac-agent `features/llm/auth/oauth_descriptors.feature` "Grok login asks for the grok-cli scope so the quota meter can read billing" (@wip; gate lines below).
- The device-code request is not recorded today (`body.scope` comes back nil): record the login's outbound requests with a form body parsed into a map so `an outbound HTTP request to "…" matches:` can read `body.scope`.
- Update `spec/isaac/agent/config/schema_spec.clj` scope fixtures as needed.

feature-baseline: isaac-agent d8f0150cfc831b57039c49fc29635cd9806da652
feature-blob: isaac-agent features/llm/auth/oauth_descriptors.feature 8076c128b68eeeb49070b604af433e52ace1801f


## Landed on main (2026-10-28)

main-sha: isaac-agent 61329e1ae05433287a746df5167f28f0a0a27683

## CI repair checkpoint (2026-10-28, scrapper@isaac-work-1)

Done: investigated default-branch CI run 40658158435. Specs 1974/0; features 947/2 in the stateful Responses scenarios at `features/llm/api/responses/stateful.feature:127,154` (outbound request 3 missing). A clean full local `bb features` on 61329e1 passed 947/0/2353, indicating a suite-order/race defect; focused stateful and OAuth+stateful passed. Root risk: `spec/isaac/agent/auth_steps.clj:59` clears the **global** outbound HTTP capture inside the Grok CLI wrapper, potentially clobbering concurrent turns. Added a concurrency regression test at `spec/isaac/agent/session/session_steps_spec.clj:183` on branch `bean/isaac-blt2-ci`; focused RED currently fails to compile because `auth-steps/with-stubbed-device-code-login` is not yet extracted. No repair commit/push yet: tests red.

Next: extract the wrapper at `spec/isaac/agent/auth_steps.clj:55` into `with-stubbed-device-code-login` without globally clearing requests, make the focused spec green, prove device-code request capture still works in `oauth_descriptors.feature`, then run `bb ci` and push a main repair commit (bean is already completed). Do not restart the service.

## CI repair landed (2026-10-28)

main-sha: isaac-agent f74547c (repair on top of a5efe91). Removed global outbound-request reset in Grok login feature wrapper; regression spec covers a concurrent turn and captured device-code request. `bb ci` passed (947 feature examples, 0 failures; 1 pre-existing pending). Earlier full run on the original 61329e1 also passed locally, so the original CI failure was intermittent. No service lifecycle action taken.
