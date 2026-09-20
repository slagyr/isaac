---
# isaac-pqqm
title: 'Auth strategies: one pluggable chain replaces token, principals and identity verifiers'
status: todo
type: feature
priority: high
tags:
    - security
    - http
created_at: 2026-09-20T00:25:07Z
updated_at: 2026-09-20T00:25:07Z
parent: isaac-gym1
---

Repo: **isaac-http**. Micah, 2026-09-19: "I imagine other people are going to want other types of authentication so this sounds like an abstraction that we need to introduce."

## Why

The module has three authentication mechanisms with no common shape: the static `:http :auth :token`, the `:http :auth :principals` map, and OIDC identity verifiers registered through the `:isaac.http/identity` berth. `auth/principals` splices the static token into the principals map as a synthetic `:admin` entry carrying a `:legacy?` flag whose only purpose is to trigger a warning. `wrap-auth` then infers whether auth is even on from which of those keys happen to exist.

## Change

One **authentication strategy** abstraction, on a new berth `:isaac.http/authenticator`. A strategy takes the request and the resolved config and returns a principal `{:name :scopes}`, a refusal `{:reason …}`, or nil to pass to the next strategy. Bearer principals and OIDC become two strategies that ship with the module; a third party contributes mutual TLS, an HMAC signature or a proxy header the same way. First non-nil wins; a refusal short-circuits with its reason (`:expired`, `:revoked`, `:unknown`).

Config names the chain in order, each entry owning its data:

```
:http {:auth {:enabled    true
              :strategies [{:id :principals :type :bearer
                            :principals {:planner {:hash "sha256:…" :scopes [:hail/send]}}}
                           {:id :google :type :oidc
                            :issuer "https://accounts.google.com"
                            :scopes [:google/push]}]}}
```

Scopes are unchanged: authentication says who you are, `:scope` on the route says what that principal may call, `require-scope!` still works.

## Legacy shapes warn, they do not validate (Micah, explicit)

No legacy validator and no legacy code path. `:http :auth :token` and a bare `:http :auth :principals` are simply where the bearer strategy reads its data when no explicit `:strategies` entry names them; they keep working. When either appears without a declared strategy, log **one warning per boot** naming the replacement. The `:legacy?` flag and its dedicated warning in `wrap-auth` are deleted — the warning belongs to config resolution, not to the request path.

## Acceptance

Scenarios (worker writes, isaac-http `features/`): the chain tries strategies in order and the first match wins; a refusal from an early strategy is not overridden by a later one; a bearer principal and an OIDC identity both authenticate under one chain; a module-contributed strategy on the berth participates; bare `:token` still authenticates and logs one warning per boot; no `:legacy?` flag survives anywhere (`grep -rn "legacy?" src` is empty).

Blocks isaac-gym1's remaining work: isaac-ews9 builds "on by default" on this chain.
