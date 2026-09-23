---
# isaac-2abl
title: 'isaac google login completes at the Isaac host: consent redirects to /google/oauth/callback, no code to copy'
status: completed
type: feature
priority: high
tags:
    - google
    - http
created_at: 2026-09-23T14:10:59Z
updated_at: 2026-09-23T14:43:19Z
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

## Handoff (worker, 2026-09-23)

Branch `bean/isaac-2abl` in **isaac-google**, one commit `036025c`
("Release 0.1.11: …"), pushed. Manifest version 0.1.10 → 0.1.11. No pin bumps,
nothing on main.

### Design

- **`src/isaac/google/logins.clj` (new)** — pending logins at
  `<root>/google/logins/<state>.edn`, holding `{:tenant :scopes :code-verifier
  :created-at}`. State and verifier are `SecureRandom` base64url; the PKCE
  challenge is RFC 7636 S256 (the RFC's known-answer vector is a spec). A
  state from the public internet becomes a file path only when it matches
  `[A-Za-z0-9_-]{8,128}` — no traversal. `TTL` is ten minutes.
- **`src/isaac/google/door.clj`** — now "the doors", push and callback.
  `public-base` = `google.<org>.oauth.redirect-base`, else the origin of
  `google.<org>.push.endpoint`; `redirect-uri` appends
  `/google/oauth/callback`, or nil (which is what keeps the `--code` flow).
  `callback-verifier` is an isaac-http **code verifier** (a fn on the
  `:isaac.http/identity` seam) granting `{:name :google/oauth-callback :scopes
  #{:google/oauth-callback}}` for GET on that path alone — that is how a
  no-bearer public route is expressed, since isaac-http has no "public" scope.
  `register-callback!` is gated on at least one configured organization:
  registering any verifier turns isaac-http's auth on for the whole server,
  and a host with no Google has no login to finish.
- **`src/isaac/google/http.clj`** — `oauth-callback`. Reads the query from
  `:query-string` (falling back to a `:uri` that still carries it), loads the
  pending login, then: unknown/unparseable state **400**, expired **410** (and
  the stale file is dropped), Google's `error=` **400**, missing code **400**,
  organization no longer configured **400**, Google refusing the exchange
  **502**. On success it exchanges with that organization's client + the
  stored verifier + the same redirect_uri, stores tokens exactly as
  `exchange-and-store!` does (`auth-store/save-tokens!` under
  `google/<org>`), deletes the pending file, logs `:google/login-completed`
  (tenant only, info) and answers **200** "Signed in as `<account>` for
  organization `<org>`. You can close this tab." Pages are plain HTML and
  everything interpolated is escaped.
- **`src/isaac/google/oauth.clj`** — `:code-challenge` on the consent URL
  (`code_challenge` + `code_challenge_method=S256`) and `:code-verifier` on
  the exchange, both omitted when absent so the legacy flow is byte-identical.
- **`src/isaac/google/cli.clj`** — with a redirect-uri, `login` writes the
  pending file, prints the URL, then polls the file every 2 s for 10 min
  (`*poll-interval-ms*` / `*poll-timeout-ms*`, dynamic so features can drive
  them). File gone ⇒ `Signed in for organization <org>` and exit 0; timeout ⇒
  stderr `Login timed out — run again, or use --code`, the stale pending file
  is dropped, exit 1. With no base, the old paste-a-code print is unchanged.
- **`src/isaac/google/config.clj` + manifest** — `oauth.redirect-base`,
  optional string (module_spec already pins manifest schema == config.clj).
  Manifest gains the `GET /google/oauth/callback` route with scope
  `:google/oauth-callback`.

### Tests

```
bb lint src        # 0 errors, 1 warning (pre-existing worker.clj)
bb spec            # 250 examples, 0 failures, 423 assertions  (was 212/340)
bb features        # 36 examples, 0 failures, 160 assertions   (was 33/134)
bb ci              # exit 0
```

`features/oauth_callback.feature` (new, 2 scenarios) covers the bean's first
three: the happy path is one scenario end-to-end (URL + pending record + the
callback + the CLI poll) because a `login` left polling past its scenario
would outlive the HTTP stub it holds open; unknown-state 400 and expired-state
410 share the second. The bean's fourth (no base ⇒ `--code` flow) is a new
scenario in `features/login.feature`, whose Background already has no push
endpoint. New module-owned steps live in `feature-steps/isaac/google_steps.clj`.

Two harness notes for whoever touches these steps:
- `isaac.http.server-steps/get-request` puts the whole path in `:uri`, and the
  suite runs in direct-handler mode (no port bound), so a query string never
  reaches the handler as a query. The callback step builds the ring request
  itself.
- `isaac is run in the background with` uses `future`, whose non-daemon pool
  thread held the JVM open past the suite and made `bb features` exit 124.
  The step here starts a **daemon** thread wrapped in `bound-fn` (gherclj's
  scenario state rides a dynamic var).

### Operator prerequisite (doc/rollout.md)

The OAuth client must list `https://<host>/google/oauth/callback` under
**Authorized redirect URIs**, exact match. Only **Web application** clients
have that field — a Desktop client cannot serve this flow, so a host moving
off the paste-a-code login needs a Web client (id + secret re-set, and one
more login). Desktop stays documented for hosts the internet cannot reach.
`google.<org>.oauth.redirect-base` covers a callback served on a different
public name than the push endpoint.

### Needs the live host

- One real `isaac google login` on yopp against a Web OAuth client with the
  redirect URI registered: the browser should land on the Isaac host and the
  terminal print `Signed in for organization <org>` with no `--code`. Nothing
  here has talked to Google.
- Known gap, deliberate: `--code` still exchanges against
  `http://localhost:1/`. On a host that has a public base, a code pasted from
  the *new* consent screen would fail with `redirect_uri_mismatch`; `--code`
  is for hosts with no callback, as the bean says. Worth a follow-up bean if
  the mixed case ever bites.

## Landed on main

main-sha: isaac-google 036025c (0.1.11)

Planner check 2026-09-23: `bb spec` 250/0, `bb features` 36/0 on 036025c. Fast-forwarded to main; registry repinned. Prerequisite found by the worker: the redirect URI can only be registered on a Web application OAuth client; yopp uses a Desktop client, so a new Web client (id + secret into google.tonotop.oauth) is needed before the flow works there. Edge left open: --code still exchanges against http://localhost:1/, so a code copied from the NEW consent screen on a host with a public base would mismatch — follow-up if it bites.
