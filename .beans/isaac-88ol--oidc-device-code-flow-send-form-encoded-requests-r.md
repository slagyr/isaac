---
# isaac-88ol
title: 'OIDC device-code flow: send form-encoded requests (RFC 8628) — xAI 415s on JSON'
status: completed
type: bug
priority: high
created_at: 2026-07-10T13:40:06Z
updated_at: 2026-07-10T13:52:22Z
---

## Bug

isaac.llm.auth.device-code/request-user-code! and poll-for-auth! use -post-json! for all flows. OpenAI's bespoke device-auth endpoints accept JSON, but standards-compliant OIDC endpoints (auth.x.ai) reject it: POST /oauth2/device/code with JSON -> 415 'Form requests must have Content-Type: application/x-www-form-urlencoded'. The CLI surfaces this as 'Failed to request device code: :unknown'.

## Observed (2026-07-10, zanebot)

isaac auth login --provider grok fails at step 1. Curl confirms: JSON body -> 415; form body -> device_code/user_code issued (proven 2026-07-08 and again today).

## Fix

- For :flow :oidc-device-code, request-user-code! and poll-for-auth! use -post-form! (exchange-tokens! already does). The openai :openai-device-auth flow keeps JSON.
- ALSO surface response detail: the 415 body carried a clear message but the CLI printed :unknown — error results should include status + body message in the CLI output (this hid the diagnosis).

## Scenario coverage (worker writes)

- The scripted OIDC endpoint ASSERTS the request Content-Type is application/x-www-form-urlencoded for device-code request, poll, and refresh (the missing tooth that let this ship).
- openai flow still sends JSON (regression guard).
- A 4xx with a message body surfaces the message in the CLI error, not :unknown.

## Scenario coverage (worker)

- `device_code_spec`: OIDC request/poll use `-post-form!`; chatgpt regression uses JSON; scripted `http/post` asserts `application/x-www-form-urlencoded` on device-code request, poll, and refresh; 415 plain-text body surfaces `:message`.
- `cli_spec`: grok login failure prints HTTP 415 + form-urlencoded message, not `:unknown`.

## Worker notes

- Repo: `isaac-agent-wpny` branch `bean/isaac-88ol`.
- `:oidc-device-code` routes request + poll through form encoding; OpenAI `:openai-device-auth` unchanged (JSON).
- `parse-response` tolerates non-JSON error bodies; CLI `format-device-code-error` includes status + message.

## Context

Blocks the isaac-wpny zanebot cutover (Micah's login attempt failed on this). Found live 2026-07-10.
