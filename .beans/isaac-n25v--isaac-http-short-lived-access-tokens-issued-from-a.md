---
# isaac-n25v
title: 'isaac-http: short-lived access tokens issued from a principal''s refresh credential — alongside static bearers, not instead'
status: draft
type: feature
priority: normal
tags:
    - http
    - security
created_at: 2026-09-19T21:03:52Z
updated_at: 2026-09-19T21:03:52Z
parent: isaac-gym1
---

Design note (Micah, 2026-09-19), parked. Prompted by watching Google's refresh/access split keep yopp signed in unattended.

Today every Isaac principal is a long-lived bearer secret (hashed in config, scoped, :expires, mint/rotate/revoke, burst-guarded). OIDC (isaac-4sqh) lets Isaac VERIFY short-lived tokens others issue; Isaac never ISSUES one. A leaked bearer is good until someone notices.

## Shape

- A principal may hold a refresh credential instead of (or as well as) a bearer. `POST /auth/token` with the refresh credential returns a signed access token, ~1h, scopes ⊆ the principal's. Routes accept access tokens like any bearer; the refresh credential is accepted at exactly that one endpoint.
- **Static bearers stay.** Micah: the refresh loop puts real weight on every client (isaac remote, CI, hail senders, ad-hoc curl). Static tokens remain the simple path; refresh/access is opt-in per principal for the callers that can carry the loop (the CLI's remote routing config can cache an access token per host).
- Server holds a signing key under ~/.isaac (rotatable; access tokens carry kid). Revocation = refuse at next refresh; nothing to rotate.
- `auth mint --refresh` produces the refresh credential; `auth list` shows kind (bearer / refresh) and last refresh.

## What it buys / costs

Buys blast radius: logs, proxies, argv (isaac-tvcg) carry hour-lived tokens. Costs: a refresh loop in each opting-in client, a signing key to manage, token caching in the CLI.

## Alternative that gets most of it cheaper

Lean on external identity for callers that have one: Tailscale node identity (every tailnet peer can prove who it is), GitHub Actions OIDC (isaac-o0jb). Static principals shrink to the few callers with neither. Decide this before building the issuer.

Scenarios when promoted; blocked by nothing; not scheduled.
