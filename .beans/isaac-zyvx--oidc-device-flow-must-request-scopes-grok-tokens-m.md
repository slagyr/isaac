---
# isaac-zyvx
title: OIDC device flow must request scopes — grok tokens minted without api:access are useless
status: todo
type: bug
priority: high
created_at: 2026-07-11T00:55:25Z
updated_at: 2026-07-11T00:55:25Z
---

## Bug

request-user-code! sends only client_id to the device endpoint. auth.x.ai grants a token with default (empty) scope, and every api.x.ai call then fails: 403 {"code":"permission-denied","error":"OAuth2 token missing required scope: api:access"}. Login LOOKS successful (tokens persisted) but the provider is unusable.

## Fix

- OAuth descriptor gains :scope; grok descriptor + :grok manifest template set "api:access offline_access" (probe-verified 2026-07-11: device endpoint accepts it and issues device_code/user_code).
- Device request includes scope for :flow :oidc-device-code (form-encoded, per isaac-88ol). OpenAI flow unchanged.
- SECONDARY (same bean): the 403 surfaced as an EMPTY SUCCESSFUL turn in isaac prompt ({"response":""} exit 0) — a provider HTTP error on the streaming path must surface as an error (and classify :reason :auth per isaac-5a4n on hail turns), never as empty text. Find and fix the swallow.

## Scenario coverage (worker writes)

- Scripted OIDC device endpoint asserts the scope parameter is present with the descriptor's value.
- A 403 permission-denied on the streamed turn: isaac prompt exits nonzero with the API message; hail turn classifies unavailable :reason :auth.
- OpenAI flow sends no scope (regression guard).

## Post-deploy note

Existing grok store tokens are scope-less and must be discarded: Micah re-runs isaac auth login --provider grok once after this ships (attempt #5, the one that works).
