---
# isaac-4sqh
title: 'isaac-http: generic OIDC/JWT verification (JWKS, iss/aud/exp) with data-shaped :isaac.http/identity trust rules — modules contribute policy, http owns the crypto'
status: in-progress
type: feature
priority: critical
tags:
    - http
    - security
created_at: 2026-09-19T02:38:06Z
updated_at: 2026-09-19T02:41:53Z
parent: isaac-gym1
---

Child of isaac-gym1 (per-principal scoped auth). Decision (Micah, 2026-09-18): OIDC/JWT verification is a general authentication practice for the HTTP module, not a Google-specific one; modules contribute TRUST RULES (issuer, audience, claim→principal), isaac-http owns the crypto. Motivation: for every client that has an identity provider (GitHub Actions, Google Pub/Sub, tailnet clients), short-lived provider-minted tokens replace long-lived bearer secrets — nothing to distribute, nothing to rotate, revocation is a config edit.

## Design
- `isaac.http.oidc` in isaac-http: verify a compact JWS/JWT — RS256/ES256 signature against a JWKS document fetched from the issuer's URL (cache by `kid`; refresh once on unknown `kid`; honour Cache-Control; **fail closed** when the JWKS is unreachable), `iss` ∈ allowed, `aud` = expected, `exp`/`nbf`/`iat` with ≤60 s skew. One outbound fetch seam (`*fetch-jwks*`) for specs.
- `:isaac.http/identity` contributions become DATA, not code: `{:issuer "https://accounts.google.com" :jwks "https://www.googleapis.com/oauth2/v3/certs" :audience <from config> :claims {:email "<from config>" :email_verified true} :principal {:name :google-pubsub :scopes #{:google/push}}}`. isaac-http registers one verifier per contribution; a bearer that is a JWT is tried against the contributions whose `iss` matches before the bearer-hash principals. Modules may still register a code verifier (existing seam) for anything the data shape cannot express.
- Config for trust rules lives with the contributing module (isaac-google's `:google :push`, a future `:ci` group) — isaac-http only owns the generic knobs (`:http :oidc {:skew-s 60 :jwks-cache-s 3600}`).
- Audit/last-used/alerts (isaac-2a2x) apply to OIDC principals unchanged; `auth list` shows them as `(oidc)` rows with issuer + audience, no hash.
- Rejections are 401 and count toward burst control (isaac-xc08 response-status counting).
- The test-only skip flag in isaac-google (`*skip-signature?*`) is deleted; the feature harness signs fixtures with a test key served by the JWKS stub.

## Scenarios (@wip, worker writes — isaac-http features/server/oidc.feature; reuse principals.feature steps + a JWKS-stub Given + a signed-token Given)
1. a bearer JWT signed by the stubbed issuer key with matching iss/aud/claims is accepted as the contributed principal with its scopes (`:http/request :principal google-pubsub`)
2. a JWT with matching claims but a signature by a different key is rejected 401 `:auth/refused :reason :signature`
3. expired / not-yet-valid / wrong `aud` / wrong `iss` are rejected (one row each)
4. an unknown `kid` triggers exactly one JWKS refresh, then accepts
5. JWKS unreachable ⇒ 401 `:reason :jwks-unavailable`, nothing accepted, one attention post if it persists past the health threshold (2a2x alert path)
6. a JWT-shaped bearer for an issuer no contribution trusts falls through to the bearer-hash path (and is refused as unknown)
7. `auth list` shows the OIDC principal with issuer + audience and no hash

## Acceptance
```
cd isaac-server && bb features features/server/oidc.feature features/server/principals.feature && bb ci
```
Version bump; rides the http train. Consumers: isaac-google (isaac-x37l), GitHub Actions CI (sibling bean).

## Handoff / resume

branch: bean/isaac-4sqh @ (push pending) (base origin/main@d082206)

**Done:** `isaac.http.oidc/verify` (RS256/ES256, JWKS cache, kid refresh, fail-closed). Data-shaped `:isaac.http/identity` map berth + `register-identity-entry!`. JWT-first in `wrap-auth` before bearer-hash; issuer-mismatch falls through (`:unknown`). JWKS-unavailable attention once. `auth list` OIDC rows. Version 0.1.19. `features/server/oidc.feature` (@jvm) + steps. JVM unit specs green (47 examples). Native `bb features features/server/oidc.feature` skips @jvm (0 examples).

**Red / blocked:** `clojure -M:test:features` cannot compile (`isaac.module.loader/invoke-add-deps!` missing — agent pin 679aee8 vs foundation 0b120cc). Native bb cannot run RSA fixture (`RSAPublicKey` not in SCI). Acceptance `bb features features/server/oidc.feature` therefore cannot prove the 9 scenarios on this host.

**Next:**
1. Pin isaac-agent (or foundation) so JVM features compile, **or** run oidc.feature under a working JVM classpath.
2. Resume at `features/server/oidc.feature:1` and `spec/isaac/http/oidc_spec.clj:81` (`register-trust-rule!`).
3. Then `bb features features/server/principals.feature && bb ci`.
