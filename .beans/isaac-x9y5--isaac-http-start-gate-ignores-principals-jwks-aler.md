---
# isaac-x9y5
title: isaac-http start gate ignores principals; jwks-alert-threshold undeclared
status: draft
type: bug
priority: normal
created_at: 2026-09-30T06:37:18Z
updated_at: 2026-09-30T06:37:18Z
---

Found overnight 2026-09-30 (handbook chapter isaac-mdj2). isaac-http's non-loopback start gate (`isaac.http.component.runtime/valid-start?`) only accepts the legacy `:http :auth :token`; a config with only `:http :auth :principals` refuses to start when bound off loopback. Confirmed by direct invocation. Also: `http.oidc.jwks-alert-threshold` is read by `isaac.http.audit` but never declared in the schema.

## Wanted

The start gate accepts principals (any configured auth) as sufficient. Declare `jwks-alert-threshold` with a description.
