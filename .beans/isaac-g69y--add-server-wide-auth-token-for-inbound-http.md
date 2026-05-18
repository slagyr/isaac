---
# isaac-g69y
title: Add server-wide auth token for inbound HTTP
status: completed
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-05-15T19:18:56Z
updated_at: 2026-05-18T20:28:24Z
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

## Direction (finalized)

A single config slot — likely `:server :auth :token` or top-level `:auth :token` — that:

1. Gates every inbound HTTP request via a Ring middleware in `src/isaac/server/http.clj` or wrapped into the route handler at `src/isaac/server/routes.clj:67`.
2. **Replaces** the per-channel `:hooks :auth :token` and `:gateway :auth :token` slots (or supersedes them via precedence). Webhook and gateway auth becomes a thin wrapper over the same middleware.
3. Supports env substitution via the existing `${TOKEN_NAME}` syntax so the token lives in `.isaac/.env`, not in committed config.
4. Refuses to start if no token is configured AND the server is bound to a non-loopback host — fail closed, not silently open.

## Decisions

1. **Slot location**: `:server :auth :token`. Server-owned; keeps related concerns together.
2. **Migration**: hard-break on `:hooks :auth :token` with a config validation error pointing to the new slot (per [[feedback_no_provider_aliases]]). `:gateway :auth :mode/:token` are silently retired — the existing unknown-key warning is enough cue. Hooks per-channel auth was a carry-over from openclaw (where each integration validated a provider-specific secret); isaac only ever used it as a second bearer-token check, so retiring it is a 1:1 replacement.
3. **Local-only mode**: no flag. Loopback bind (anything `InetAddress/isLoopbackAddress` accepts — covers 127.0.0.0/8, `::1`, etc.) is trusted without a token. Non-loopback bind requires the token or the server refuses to start.
4. **Multiple tokens**: single shared token for v1. Per-channel scopes are a follow-up bean if/when we need revocation per surface.
5. **CLI auth wire shape**: `Authorization: Bearer <token>`. The existing `-T/--token` flag in CLI commands serializes to that header. No `?token=` query-string variant.

## Spec

Nine @wip scenarios committed in `features/server/auth.feature` (commit 8cafd71b):

- A request with the configured Bearer token reaches the handler
- A request with no Authorization header is rejected (401 + `WWW-Authenticate: Bearer.*`)
- A request with the wrong token is rejected
- Loopback bind allows unauthenticated requests when no token is configured
- Loopback bind ignores a configured token (no auth required)
- IPv6 loopback bind is treated the same as 127.0.0.1
- Non-loopback bind without a token refuses to start
- Old `:hooks :auth :token` slot fails validation pointing to the new slot
- Token supports `${ENV_VAR}` substitution from the state dir env

## Implementation surfaces

- `src/isaac/server/http.clj` — new Ring middleware that enforces the bearer token on every inbound request; skips on loopback.
- `src/isaac/server/routes.clj` — wire the middleware in front of the route handler.
- `src/isaac/server/cli.clj` (or wherever `app/start!` resolves the bind host) — refuse to start on a non-loopback host when `:server :auth :token` is unset; log `:server/auth-required` and bail.
- `src/isaac/config/schema.clj` — add `:server :auth :token` (string, optional, env-substitutable). Retire `:hooks :auth :token` with a `:retired` validator producing the "retired ... use :server :auth :token" error. Drop `:gateway :auth :mode/:token` from the schema entirely.
- `src/isaac/hooks.clj:114-156` — drop `bearer-token`/`auth-ok?`/401 branch; rely on the global middleware.
- `src/isaac/comm/acp/websocket.clj:34-46` — delete `auth-error-response`; the WS upgrade now goes through the same middleware.
- New step (if it doesn't exist): `Given the env var "X" is set to "Y"` for the env-substitution scenario.

## Definition of done

- All nine @wip scenarios pass; their `@wip` tags are removed.
- `bb features` full suite stays green.
- `bb spec` stays green.
- Existing webhook tests still pass through the new middleware.
- A live `isaac server` bound to `0.0.0.0` without `:server :auth :token` refuses to start.
- A live `isaac server` bound to `127.0.0.1` without a token serves `/status` without authentication.

## Verification commands

- `bb features features/server/auth.feature`
- `bb spec`
- `bb features`

## Related

- [[feedback_no_provider_aliases]] — apply the same principle to the old per-channel auth keys



## Verification failed

Feature-file history fails the verify gate. In the implementation commit `d9560483`, `features/server/auth.feature` changed more than `@wip` removal: the "Non-loopback bind without a token refuses to start" scenario rewrote the expected log-message cell from a plain string pattern `.*:server :auth :token.*non-loopback.*` to an EDN regex literal `#".*:server :auth :token.*non-loopback.*"`. That assertion-shape change is not described anywhere in the bean and there is no `## Exceptions` section authorizing it, so step 1 fails. I did run `bb features features/server/auth.feature` and `bb spec`, both of which passed, and `bb features` still fails elsewhere in the repo with unrelated pre-existing failures, but per the verify gate this bead must fail on the unauthorized feature edit before later checks can matter.



## Verification failed

Re-verified after the latest updates. The implementation feature file still contains an unauthorized edit beyond `@wip` removal. In commit `d9560483`, `features/server/auth.feature` changed the expected log-message cell in the "Non-loopback bind without a token refuses to start" scenario from a plain string pattern to an EDN regex literal (`#".*:server :auth :token.*non-loopback.*"`). That assertion-shape change is not described anywhere in the bean and there is no `## Exceptions` section authorizing it, so the feature-history gate still fails at step 1. I also re-ran `bb features features/server/auth.feature` and `bb spec`; both passed, and `bb features` remains red elsewhere in the repo with unrelated failures, but those later checks do not change the step-1 failure.
