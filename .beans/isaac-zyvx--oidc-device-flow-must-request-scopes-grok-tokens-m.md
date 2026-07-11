---
# isaac-zyvx
title: OIDC device flow must request scopes — grok tokens minted without api:access are useless
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-07-11T00:55:25Z
updated_at: 2026-07-11T01:12:20Z
---

## Bug

request-user-code! sends only client_id to the device endpoint. auth.x.ai grants a token with default (empty) scope, and every api.x.ai call then fails: 403 {"code":"permission-denied","error":"OAuth2 token missing required scope: api:access"}. Login LOOKS successful (tokens persisted) but the provider is unusable.

## Fix

- OAuth descriptor gains :scope; grok descriptor + :grok manifest template set "api:access offline_access" (probe-verified 2026-07-11: device endpoint accepts it and issues device_code/user_code).
- Device request includes scope for :flow :oidc-device-code (form-encoded, per isaac-88ol). OpenAI flow unchanged.
- SECONDARY (same bean): the 403 surfaced as an EMPTY SUCCESSFUL turn in isaac prompt ({"response":""} exit 0) — a provider HTTP error on the streaming path must surface as an error (and classify :reason :auth per isaac-5a4n on hail turns), never as empty text. Find and fix the swallow.

## Scenario coverage (worker)

- `device_code_spec`: OIDC device request form body includes `scope` (`api:access offline_access`); chatgpt JSON request has no scope.
- `provider_walls.feature`: 403 permission-denied → unavailable reason auth.
- `cli-prompt.feature`: grover 403 http-error → exit 1, stderr contains API message (not empty JSON success).
- Manifest + `grok-descriptor` carry `:scope`.

## Worker notes

- Repo: `isaac-agent` `bean/isaac-zyvx`.
- `provider-wall/normalize` runs immediately after `tool-loop/run` so SSE HTTP errors classify before empty-terminal guard; `prompt_cli` treats `:unavailable?` as failure.

## Post-deploy note

Existing grok store tokens are scope-less and must be discarded: Micah re-runs isaac auth login --provider grok once after this ships (attempt #5, the one that works).
