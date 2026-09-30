---
# isaac-x9y5
title: isaac-http start gate ignores principals; jwks-alert-threshold undeclared
status: draft
type: bug
priority: normal
created_at: 2026-09-30T06:37:18Z
updated_at: 2026-09-30T15:19:08Z
blocked_by:
    - isaac-t95z
---

Found overnight 2026-09-30 (handbook chapter isaac-mdj2). isaac-http's non-loopback start gate (`isaac.http.component.runtime/valid-start?`) only accepts the legacy `:http :auth :token`; a config with only `:http :auth :principals` refuses to start when bound off loopback. Confirmed by direct invocation. Also: `http.oidc.jwks-alert-threshold` is read by `isaac.http.audit` but never declared in the schema.

## Wanted

The start gate accepts principals (any configured auth) as sufficient. Declare `jwks-alert-threshold` with a description.

## Decision (Micah, 2026-09-30)

- Remove the non-loopback start gate entirely: an intranet server may run without auth. Instead, when the server binds beyond loopback with no auth configured (no token, no principals, no oidc), it logs one warning at startup and starts.
- Declare `http.oidc.jwks-alert-threshold` in the http schema with `:default 1` and a description; drop the `(or … 1)` fallback in `isaac.http.audit`.
- Update the http handbook chapter (it currently notes the field is undeclared).
