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
