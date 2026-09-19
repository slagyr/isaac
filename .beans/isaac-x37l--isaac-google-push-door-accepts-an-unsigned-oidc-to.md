---
# isaac-x37l
title: 'isaac-google push door: verify Google''s OIDC token via isaac-http''s generic verifier (today it accepts an UNSIGNED token — aud+email only); gate for exposing the door'
status: in-progress
type: bug
priority: critical
tags:
    - google
    - security
created_at: 2026-09-19T02:28:39Z
updated_at: 2026-09-19T18:52:36Z
parent: isaac-bv1l
blocked_by:
    - isaac-4sqh
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


## Re-scoped (2026-09-18, Micah): use the generic verifier

The crypto moves to isaac-http (**isaac-4sqh**): JWKS fetch/cache, signature, iss/aud/exp. This bean becomes: replace `identity.clj`'s hand-rolled check with a DATA contribution to `:isaac.http/identity` — issuer `https://accounts.google.com`, JWKS `https://www.googleapis.com/oauth2/v3/certs`, audience `:google :push :endpoint`, claims `{:email <:google :push :service-account> :email_verified true}`, principal `{:name :google-pubsub :scopes #{:google/push}}` — and delete `*skip-signature?*`. Scenarios 1–5 above stay but the JWKS stub/signing steps come from isaac-http's spec-support. Blocked by isaac-4sqh.



## Handoff / resume (planner, 2026-09-19)
Split: the isaac-http half is **isaac-401c** (config refs in trust rules; branch bean/isaac-x37l in isaac-http @ 39efb60). This bean is the isaac-google half: branch bean/isaac-x37l in isaac-google @ 6bd0ad4 (base origin/main@ca9fac6). Done: manifest trust rule replaces identity.clj (deleted, with *skip-signature?*), push_door.feature has scenarios 1–5 plus burst counting (10/10), bb ci 42 spec + 19 feature examples green. Blocked on 401c landing only because deps/bb pin http at the branch sha; once 401c lands the planner repins to the main sha, reruns bb ci, and tags unverified.



isaac-401c landed (isaac-http main 493416d). Repinned; cold-cache classpath ok; bb ci 42 spec + 19 feature examples green. branch: bean/isaac-x37l @ c3ca39d (base origin/main@ca9fac6) in isaac-google — fast-forward from main. Version 0.1.3. Handed to verify.



## Verify fail (attempt 1, 2026-09-19): push_door.feature rewritten beyond @wip; no ## Exceptions

HEAD isaac-google: c3ca39d (bean/isaac-x37l). Working tree: clean. Base origin/main@ca9fac6.

verify.md §1 — permitted feature edits are @wip removal or bean ## Exceptions. There is no ## Exceptions section. Remaining checks were not run.

features/push_door.feature (commit 6bd0ad4) rewrote planner/planted wording AND added scenarios:

1. Feature blurb rewritten (isaac-1jep wording → isaac-4sqh/x37l description of JWKS/iss/aud).
2. Existing "wrong audience / wrong email / unsigned" scenario gained extra Then log-matching rows (:audience, :claims, :signature) — reworded/strengthened assertions, not @wip removal.
3. Five new scenarios appended (foreign-key signature, expired/wrong-iss, JWKS unreachable, kid refresh, burst counting). The bean listed those as @wip for the worker to write, but they were never planted as @wip on origin/main — they are new feature content without ## Exceptions.

Do not land. Restore the planted 1jep wording for the existing scenarios (keep only @wip removal if any), or get a ## Exceptions entry that names the blurb rewrite, the extra Then rows, and the five new scenarios. Then re-hand for verify.
