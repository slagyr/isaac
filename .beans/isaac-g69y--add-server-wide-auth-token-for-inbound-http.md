---
# isaac-g69y
title: Add server-wide auth token for inbound HTTP
status: draft
type: feature
priority: normal
created_at: 2026-05-15T19:18:56Z
updated_at: 2026-05-15T19:18:56Z
---

## Problem

Isaac has fragmented inbound auth:

| Surface | Auth source | Default behavior |
|---|---|---|
| Webhook hooks | `:hooks :auth :token` at `src/isaac/hooks.clj:95` | Required if configured |
| Gateway / ACP WebSocket | `:gateway :auth :mode/:token` at `src/isaac/comm/acp/websocket.clj:40` | Required if configured |
| HTTP server (`/status`, future routes) | none — `src/isaac/server/routes.clj` | **wide open** |
| LLM provider auth | outbound only | n/a |

Two separate token slots for two separate inbound channels, plus an unauthenticated HTTP route registry that any new endpoint inherits as "no auth." Openclaw uses a single server-wide bearer that gates the whole inbound HTTP surface — we should do the same.

## Desired direction (draft — refine before working)

A single config slot — likely `:server :auth :token` or top-level `:auth :token` — that:

1. Gates every inbound HTTP request via a Ring middleware in `src/isaac/server/http.clj` or wrapped into the route handler at `src/isaac/server/routes.clj:67`.
2. **Replaces** the per-channel `:hooks :auth :token` and `:gateway :auth :token` slots (or supersedes them via precedence). Webhook and gateway auth becomes a thin wrapper over the same middleware.
3. Supports env substitution via the existing `${TOKEN_NAME}` syntax so the token lives in `.isaac/.env`, not in committed config.
4. Refuses to start if no token is configured AND the server is bound to a non-loopback host — fail closed, not silently open.

## Open questions

- **Top-level `:auth` vs nested `:server :auth`?** Top-level reads cleaner; nested keeps server config self-contained.
- **Migration path** for existing `:hooks :auth :token` / `:gateway :auth :token` configs — auto-migrate on load, warn-and-fall-back, or hard-break with a config error pointing to the new slot? Per [[feedback_no_provider_aliases]] preference, lean toward hard-break.
- **Local-only mode** — is there a `:server :allow-unauthenticated true` escape hatch for dev, or do we always require a token? Probably "always require, but loopback bind skips the check" is the safe default.
- **Multiple tokens / scopes** — single shared token is the openclaw model. Do we want per-channel tokens later (e.g. webhook token separate from gateway token for revocation)? Probably yes eventually, but v1 is one token.
- **CLI auth** (`isaac chat --token`) — already takes `-T/--token`; the wire shape there must match the new middleware's expectations.

## Acceptance (rough — sharpen during refinement)

- One config slot governs all inbound HTTP auth.
- Default-deny: server refuses to bind to a non-loopback interface without a configured token.
- Webhook and gateway requests authenticate through the same middleware; their per-channel `:auth :token` slots are gone or aliased.
- The HTTP routes registry (`/status`, anything future) requires the token.
- Tests cover: valid token / missing token / wrong token / loopback-without-token / non-loopback-without-token-startup-refusal.

## Related

- [[feedback_no_provider_aliases]] — apply the same principle to the old per-channel auth keys
