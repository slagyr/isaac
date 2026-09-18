---
# isaac-6aw3
title: 'isaac-google: module skeleton + OAuth login for the Google user (scopes berth, auth store)'
status: todo
type: feature
priority: high
tags:
    - google
created_at: 2026-09-18T04:12:15Z
updated_at: 2026-09-18T04:37:02Z
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

## Scenarios (approved 2026-09-18, Micah) — committed `@wip` in isaac-google `4dc6b8d` `features/login.feature`

Design pinned by the scenarios: command is **`isaac google login`** (module CLI berth; not `auth login --provider`, which resolves LLM provider templates); **authorization-code flow with `--code` paste** (Google refuses the device flow for Chat/Gmail scopes; paste is what a headless host needs); the token POST goes through `isaac.llm.http` so request-match steps see it; tokens under provider `google` in `isaac.llm.auth.store`.

New module-owned step phrases (implement in `feature-steps/isaac/google_steps.clj`, one helper each — everything else is existing foundation/agent steps):
- `the Google token endpoint returns access token {at} and refresh token {rt} expiring in {n}` / `… returns access token {at} expiring in {n}` / `… rejects refresh with {error}`
- `the google auth store has access {at} and refresh {rt}` / `the google auth store has an expired access token with refresh {rt}`
- `the google access token is resolved`
- `the skybeam fixture module contributes the Google scope {scope}` (fixture manifest under `test-resources/marigold/`)
- `the error mentions {text}`

## Acceptance

Definition of done: `@wip` removed from `features/login.feature`, and:

```
cd isaac-google && bb features features/login.feature:17
cd isaac-google && bb features features/login.feature:30
cd isaac-google && bb features features/login.feature:40
cd isaac-google && bb features features/login.feature:48
cd isaac-google && bb features features/login.feature:54
cd isaac-google && bb ci
```

Unit specs for: auth-URL construction (scopes union, redirect, state), code exchange, refresh, `invalid_grant` message, config validation of `:google`.

## Out of scope

Pub/Sub, any Google API call beyond OAuth, service-account auth.

Repo exists (created 2026-09-18, scaffold on main: manifest `:isaac.google`, module + spec, bb ci, hooks, CI). `slagyr-assistant` invited with write.

Secrets rule (aligned with isaac-gym1): `:oauth {:client-id … :client-secret "${GOOGLE_CLIENT_SECRET}"}` — the secret comes from `.env`, never plaintext in isaac.edn; user tokens live only in the auth store. `config get` must stay safe to read.

## Push access note (2026-09-18 04:40Z, plan)

zanebot pushes as `slagyr-assistant` (ssh key). Its **write invitation to slagyr/isaac-google is pending** and its `gh` token on zanebot is broken, so a worker cannot accept it from there. Until Micah accepts the invitation as slagyr-assistant, the worker will land green commits locally and fail to push (the isaac-mcp qgtn episode). If that happens: HOLD with the branch name and sha; do not retry the push in a loop.

Dispatched: hail 3533ee28 2026-09-18T04:38Z (band isaac-work)

Update 2026-09-18 05:27Z: slagyr-assistant write invitations on isaac-google/gchat/gmail are ACCEPTED (Micah). Push is unblocked.
