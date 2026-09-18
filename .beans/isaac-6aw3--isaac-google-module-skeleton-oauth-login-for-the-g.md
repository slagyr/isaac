---
# isaac-6aw3
title: 'isaac-google: module skeleton + OAuth login for the Google user (scopes berth, auth store)'
status: draft
type: feature
priority: high
tags:
    - google
created_at: 2026-09-18T04:12:15Z
updated_at: 2026-09-18T04:12:15Z
parent: isaac-bv1l
---

First brick of isaac-google. A module that owns the Google user's OAuth and nothing else yet.

## Scope

- New repo `isaac-google`, module id `:isaac.comm.google`? — no: it is not a comm. Use **`:isaac.google`** (shared plumbing). Manifest, README, bb/ci wiring copied from isaac-mcp (native bb specs + JVM features).
- `isaac auth login google` (CLI berth): authorization-code flow for the Google user, scopes = union of `:isaac.google/scopes` berth contributions from installed modules (declared here; gchat/gmail contribute later). Tokens saved through `isaac.llm.auth.store` under provider `google`; refresh via `refresh-oauth-tokens!`. Client id/secret from config `google.edn` (`${VAR}` substitution as everywhere).
- A `google/token` fn other modules call for a valid access token (refreshes when needed, never prompts).
- Config table `:google` in `:isaac.config/schema`: `:project`, `:topic`, `:oauth {:client-id :client-secret :account}`.

## Decisions (2026-09-18)

Refresh tokens for an internal Workspace app do not expire; a weekly re-login means the consent screen is still in Testing — that is an ops fix, not code. The login command must say so when Google returns a 7-day token.

## Scenarios to draft (feature-first; none written yet)

1. `isaac auth login google` against a stubbed token endpoint stores refresh + access tokens; `google/token` returns the access token.
2. An expired access token is refreshed silently on the next `google/token`.
3. Scopes requested = union of contributions (a fixture module contributes one extra scope).
4. Missing `:google` config fails closed with a clear validate error.

## Out of scope

Pub/Sub, any Google API call beyond OAuth, service-account auth.

Blocker: the `slagyr/isaac-google` repo must exist (Micah creates; workers clone on demand).
