---
# isaac-2abl
title: 'isaac google login completes at the Isaac host: consent redirects to /google/oauth/callback, no code to copy'
status: in-progress
type: feature
priority: high
tags:
    - google
    - http
created_at: 2026-09-23T14:10:59Z
updated_at: 2026-09-23T14:15:09Z
---

## Why (Micah, 2026-09-23)

Today `isaac google login` prints a consent URL with redirect_uri http://localhost:1/, the browser dead-ends, and the operator copies the code out of the address bar and runs the command again with --code. Three logins in one evening made the point.

## Change

- The login builds the consent URL with redirect_uri = <public base>/google/oauth/callback, where the public base is derived from the organization's configured push endpoint (google.<org>.push.endpoint, e.g. https://<host>/google/pubsub → https://<host>) or an explicit google.<org>.oauth.redirect-base. PKCE + a state nonce bound to the pending login (tenant, scopes, created-at, 10-minute expiry) kept in state, not config.
- isaac-http route GET /google/oauth/callback, public (no bearer, like the push door): validates state against the pending login, exchanges the code with the organization's client, stores the token under google/<org> exactly as --code does today, marks the pending login done, and answers a plain page: 'Signed in as <account> for organization <org>. You can close this tab.' Errors answer a page naming the failure; nothing is logged at info with a code or token.
- The CLI, after printing the URL, polls the pending login for up to 10 minutes and prints the outcome, so the terminal ends the same way the old flow did without a second command. --code remains for hosts with no public route.
- Scopes: the same union as today.

## Operator prerequisite

The redirect URI must be registered on the OAuth client in the GCP project (APIs & Services → Credentials → the client → Authorized redirect URIs: https://<host>/google/oauth/callback). Document it in doc/rollout.md next to the existing client setup.

## Scenarios (isaac-google features)

- login prints a URL whose redirect_uri is the host callback and records a pending login with state.
- the callback with a matching state exchanges the code and stores the token; the CLI poll reports signed in.
- the callback with an unknown or expired state answers an error page and stores nothing.
- a host with no push endpoint and no redirect-base falls back to the --code flow.

## Acceptance

bb spec / bb features / bb ci green in isaac-google (and isaac-http if the route registration needs a berth change); one-time on yopp: `isaac google login` ends with the browser at the Isaac host and the token stored, no --code.

## Related

isaac-x37l (OIDC verification in isaac-http), isaac-q1iu (trust rules from config), the yopp rollout record (three re-logins on 2026-09-22/23).
