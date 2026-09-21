---
# isaac-q1iu
title: 'isaac-http: OIDC trust rules from config (http.auth.identity) — trusting an issuer is configuration, not a module change'
status: draft
type: feature
priority: high
tags:
    - http
    - security
created_at: 2026-09-21T17:34:41Z
updated_at: 2026-09-21T17:34:41Z
parent: isaac-gym1
---

Child of isaac-gym1. Micah, 2026-09-21: "the trust rule sounds like
configuration, not a code change."

Today the OIDC verifier (isaac-4sqh) only learns trust rules from code:
module manifests (`:isaac.http/identity`) or runtime registration
(`isaac.http.auth/register-identity-entry!`, which isaac-google calls per
tenant). There is no way to declare "trust this issuer as this principal" in
isaac.edn. So trusting GitHub Actions — pure policy — would mean editing a
module and deploying it.

## Shape

`http.auth.identity` is a map of rule-id → the same data-shaped rule the
verifier already accepts, hot-reloaded like the principals beside it:

```clojure
:http {:auth {:identity
              {:github-ci
               {:issuer    "https://token.actions.githubusercontent.com"
                :jwks      "https://token.actions.githubusercontent.com/.well-known/jwks"
                :audience  "https://<host>/hail/send"
                :claims    {:repository_owner "slagyr" :ref "refs/heads/main"}
                :principal {:name :github-ci :scopes #{:hail/send}}}}}}
```

- `identity-rules` merges config rules with registered ones; a config rule
  with the same id as a registered rule wins (operator overrides module).
- Schema under `http.auth.identity` (issuer, jwks, audience, claims,
  principal {name, scopes, expires?}); `config validate` refuses a rule
  missing issuer/jwks/audience/principal.
- `isaac http auth list` shows config OIDC principals with issuer and
  audience and no hash, as it does for registered ones.
- Rule values may still be config refs (isaac-401c) — pointless in config
  but harmless.

## Scenarios (isaac-server `features/server/oidc.feature`)

- a JWT matching a rule declared under `http.auth.identity` is accepted as
  that principal and authorized for the rule's scopes (stubbed JWKS)
- a config rule appears in `auth list`; an invalid one fails `config validate`
- adding a rule to isaac.edn takes effect on hot reload without restart

Unblocks isaac-o0jb (GitHub Actions) and the Zap draft; neither then needs
a module change.
