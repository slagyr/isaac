---
# isaac-161q
title: 'Public scopes: routes and operators can exempt scopes from auth'
status: todo
type: feature
priority: high
tags:
    - security
    - http
created_at: 2026-09-20T00:25:07Z
updated_at: 2026-09-20T00:25:14Z
parent: isaac-gym1
blocked_by:
    - isaac-pqqm
---

Repo: **isaac-http**. Blocked by the strategy chain bean. Micah, 2026-09-19: "We will need a way to white-label certain scopes so that they don't require any auth. Public routes."

## Why

With auth on by default, a route that must answer anonymously — a health check, a future webhook — has no way to say so. Today "no scope" means admin, which is the right default, but there is no opposite.

## Change

Hang the exemption off the scope vocabulary that routes already declare, so there is one axis and not two.

- A route may declare `:scope :public` on its `:isaac.http/route` entry when it is inherently open. `wrap-auth` serves it before authenticating and records no principal.
- An operator may widen per host: `:http :auth {:public-scopes #{:health}}`. Both are empty by default, so opening a route is deliberate and greppable.
- A public route is still counted by burst control, which is the only protection left in front of it.
- Google's Pub/Sub door stays authenticated through its OIDC identity; it is not public and must not be listed as an example of one.

## Acceptance

Scenarios (worker writes, isaac-http `features/`): a `:scope :public` route answers with no credentials while a sibling route on the same server still demands them; a scope listed in `:public-scopes` answers anonymously; removing it from the list restores 401; an anonymous request to a public route carries no `:isaac/principal`; repeated anonymous 401s elsewhere still trip burst.
