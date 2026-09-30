---
# isaac-x9y5
title: isaac-http start gate ignores principals; jwks-alert-threshold undeclared
status: todo
type: bug
priority: normal
created_at: 2026-09-30T06:37:18Z
updated_at: 2026-09-30T16:51:42Z
blocked_by:
    - isaac-fkqz
---

Found overnight 2026-09-30 (handbook chapter isaac-mdj2). isaac-http's non-loopback start gate (`isaac.http.component.runtime/valid-start?`) only accepts the legacy `:http :auth :token`; a config with only `:http :auth :principals` refuses to start when bound off loopback. Confirmed by direct invocation. Also: `http.oidc.jwks-alert-threshold` is read by `isaac.http.audit` but never declared in the schema.

## Wanted

The start gate accepts principals (any configured auth) as sufficient. Declare `jwks-alert-threshold` with a description.

## Decision (Micah, 2026-09-30)

- Remove the non-loopback start gate entirely: an intranet server may run without auth. Instead, when the server binds beyond loopback with no auth configured (no token, no principals, no oidc), it logs one warning at startup and starts.
- Declare `http.oidc.jwks-alert-threshold` in the http schema with `:default 1` and a description; drop the `(or … 1)` fallback in `isaac.http.audit`.
- Update the http handbook chapter (it currently notes the field is undeclared).

## Acceptance (gated, Micah approved 2026-09-30)

- The @wip scenarios in isaac-http `features/server/auth.feature` (no-auth non-loopback start warns `:server/auth-absent`; principals-only starts without the warning) and `features/http/config.feature` (jwks-alert-threshold default 1; accepts a configured value) pass with @wip removed.
- One-time: the old "Non-loopback bind without a token refuses to start" scenario and the feature-description sentence saying so are deleted; `valid-start?` no longer refuses for missing auth (the dropped-auth-key refusal stays).
- Handbook chapter updated (threshold now declared; startup warning described).
- `bb ci` green.

feature-baseline: isaac-http 7fd602ab7ca1ca057781553fbfb81e298ad491ac
feature-blob: isaac-http features/server/auth.feature 231c6fd2835552a69e7e7ddd014c44f753f9044e
feature-blob: isaac-http features/http/config.feature 87aa73eb1c82dbd68e3802de53935e3fe8c17895
