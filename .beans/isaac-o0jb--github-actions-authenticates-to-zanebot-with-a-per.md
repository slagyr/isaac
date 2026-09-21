---
# isaac-o0jb
title: GitHub Actions authenticates to zanebot with a per-job OIDC token — retire the ISAAC_SERVER_AUTH_TOKEN CI secret
status: draft
type: feature
priority: high
tags:
    - security
    - ci
created_at: 2026-09-19T02:38:06Z
updated_at: 2026-09-21T17:34:41Z
parent: isaac-gym1
blocked_by:
    - isaac-4sqh
    - isaac-q1iu
---

Child of isaac-gym1. Blocked by the isaac-http OIDC verifier bean. Retires the long-lived `ISAAC_SERVER_AUTH_TOKEN` CI secret: GitHub Actions mints an OIDC token per job (`id-token: write` permission; `https://token.actions.githubusercontent.com`, JWKS `…/.well-known/jwks`), audience = the zanebot hail URL, claims `repository_owner` = slagyr (and optionally `repository`/`ref`). The CI Failure Hail workflow presents that token as the bearer; zanebot trusts it via a data contribution — proposed home: isaac-hail (it owns the hail route; `:isaac.http/identity {:issuer … :audience … :claims {:repository_owner "slagyr"} :principal {:name :github-ci :scopes #{:hail/send}}}`) with the audience/owner in `:hail :ci` config.

## Acceptance
- Scenario (isaac-hail): a hail sent with a GitHub-shaped OIDC token (stubbed JWKS) for the trusted owner is accepted with `:principal github-ci`; a token for another owner is refused 401.
- The `ci-failure-hail.yml` workflow in one repo (isaac-agent) switched to `id-token: write` + the OIDC bearer; CI Failure Hail arrives on zanebot with `:principal github-ci` in the hail record.
- Then roll the workflow change to every repo (one commit each) and delete the `ISAAC_SERVER_AUTH_TOKEN` secret from the org — one-time acceptance, not a scenario.



## Planner note (prowl, 2026-09-21)

Micah: the trust rule is configuration, not a module change. This bean no longer touches isaac-hail. Blocked by isaac-q1iu (trust rules from `http.auth.identity`); once that lands the GitHub half is: the config entry on zanebot (principal `github-ci`, scope `hail/send`, claims `repository_owner` slagyr + `ref` refs/heads/main), `id-token: write` + `core.getIDToken(<hail url>)` in `slagyr/orchestration` `ci-failure-hail-reusable.yml`, the same permission in each of the 21 callers (isaac-episodes' caller is stale and gets fixed in the pass), then delete the per-repo `ISAAC_SERVER_AUTH_TOKEN` secrets and zanebot's legacy `:http :auth :token`. Symptom today: CI Failure Hail runs fail with `Isaac hail failed (401)`; CI Tests are green.
