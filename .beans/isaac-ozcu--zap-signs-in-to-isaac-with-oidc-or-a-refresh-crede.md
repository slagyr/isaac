---
# isaac-ozcu
title: Zap signs in to Isaac with OIDC or a refresh credential — no long-lived bearer pasted into the app
status: draft
type: feature
priority: normal
tags:
    - http
    - security
created_at: 2026-09-21T17:34:41Z
updated_at: 2026-09-21T17:34:41Z
parent: isaac-gym1
blocked_by:
    - isaac-q1iu
---

Micah, 2026-09-21: "it would be great if Isaac also allowed OIDC from the
Zap app. Currently I have to put in the tokens in Zap; it'd be nice if Zap
could call zanebot without resetting the token."

Parked draft. Nothing in the tracker or the repos mentions Zap yet — the
first job of this bean is to pin down what Zap signs in with.

## Two ways to get there

1. **Zap presents a third-party id token.** If Zap can sign the user in
   with an identity provider (Google, Apple, GitHub, …), Isaac trusts that
   issuer through a config rule (blocked-by bean): audience = Zap's OAuth
   client id, claims `{:email "<micah's address>" :email_verified true}`,
   principal `{:name :zap :scopes #{…}}`. Zap sends the fresh id token as
   the bearer on every call; the provider's refresh token in the app keeps
   it fresh. Zero Isaac secrets in the app. Cost: Zap needs the sign-in
   flow, and the claims pin one human, so it is per-user config.
2. **Isaac issues the short-lived token itself** — isaac-n25v (refresh
   credential → hourly access token from `POST /auth/token`). Zap holds
   one refresh credential, never a static bearer; rotation is "refuse at
   next refresh". No third-party sign-in needed. Cost: n25v is unbuilt.

Lean 1 if Zap already has a sign-in; otherwise 2 is the smaller lift for a
single-user app.

## Open questions (Micah)

- What is Zap, and does it already sign in with any provider?
- Which routes does it call (so the principal's scopes are known)?
- One user or many?

## Acceptance (to be cut once the questions are answered)

- Zap reaches zanebot with no long-lived Isaac bearer configured in the app;
  a rotated Isaac principal does not require touching Zap.
