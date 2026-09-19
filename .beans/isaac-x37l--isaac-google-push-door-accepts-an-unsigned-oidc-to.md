---
# isaac-x37l
title: isaac-google push door accepts an UNSIGNED OIDC token — aud+email only, no signature/iss/exp check; anyone can inject Chat/Gmail events (gate for exposing the door)
status: todo
type: bug
priority: critical
tags:
    - security
    - google
created_at: 2026-09-19T02:28:39Z
updated_at: 2026-09-19T02:28:39Z
parent: isaac-bv1l
---

## Problem (found 2026-09-18 reading isaac-google `src/isaac/google/identity.clj`)

The Pub/Sub push identity source accepts a bearer as Google when `aud` = `:google :push :endpoint` and `email` = `:google :push :service-account` and a signature SEGMENT is present (`identity.clj:62`: `(seq (nth (jwt-parts token) 2 nil))`). The signature is never verified, `iss` and `exp` are never checked. Both accepted claims are non-secret (the endpoint URL is the door; the service-account email appears in GCP console/docs). Anyone can mint a JWT with those two claims and be accepted as principal `google-pubsub` with scope `:google/push` — i.e. inject arbitrary Chat/Gmail "events" into the durable inbox, which become prompts to the Google crew. The `*skip-signature?*` seams exist for tests, but production is effectively skip-signature already.

## Required before the door is exposed (hard gate for enabling Funnel on any host running isaac-google)
1. Verify the JWT per Google's push-auth contract: RS256 signature against the JWKS at `https://www.googleapis.com/oauth2/v3/certs` (cache by `kid`, refresh on unknown kid, respect Cache-Control), `iss` ∈ {`accounts.google.com`, `https://accounts.google.com`}, `exp`/`iat` with small skew, `aud` = endpoint, `email` = service account AND `email_verified` true. Reject otherwise.
2. The JWKS fetch is the ONE outbound call; failure ⇒ reject (fail closed) with `:google/push-rejected :reason :jwks-unavailable` and attention if it persists (isaac-fu2m health).
3. Keep `*skip-signature?*` for the feature harness only; a production config cannot set it (no config key).
4. Rejections count toward burst control automatically once isaac-xc08 lands (response-status counting) — verify with a scenario.

## Scenarios (@wip, worker writes — isaac-google features/push_door.feature; existing steps for the door + a JWT fixture; add a JWKS stub step)
1. a push whose token is signed by the stubbed JWKS key with the right aud/email/iss/exp is accepted (204, inbox record)
2. a push whose token has the right claims but a signature by another key is rejected (401, nothing in the inbox, `:google/push-rejected :reason :signature`)
3. an expired token is rejected; wrong `iss` is rejected
4. JWKS unreachable ⇒ rejected, `:reason :jwks-unavailable`, no inbox write
5. an unknown `kid` triggers one JWKS refresh, then accepts

## Acceptance
```
cd isaac-google && bb features features/push_door.feature && bb ci
```
Field: on yopp with Funnel on, a hand-crafted unsigned JWT with the right aud/email → 401; a real Pub/Sub push → 204 and `:google/push-received`.
