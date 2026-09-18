---
# isaac-bzgw
title: 'isaac-http: principals + scopes in wrap-auth; :scope on the route berth; request principal; require-scope!; legacy token = admin; hot-reload'
status: draft
type: feature
priority: high
tags:
    - security
created_at: 2026-09-18T04:13:56Z
updated_at: 2026-09-18T04:13:56Z
parent: isaac-gym1
---

Child 1 of isaac-gym1 (principals epic). Repo: isaac-http (local checkout `isaac-server`).

## Work
- Config schema: `:server :auth :principals {name {:hash str :scopes #{kw} :expires str?}}` (isaac-http's `:isaac.config/schema` contribution). `:hash` = `"sha256:<hex>"` of the plaintext token. Validation: scopes non-empty; expires ISO date.
- Route berth `:isaac.http/route` schema gains optional `:scope :keyword`; `register-route-entry!` keeps it on the entry. No `:scope` ⇒ requires `:*`.
- `wrap-auth`: bearer → SHA-256 → principal lookup (constant-time compare across all hashes; never log the bearer); expired ⇒ 401; route scope ∉ principal scopes and `:*` ∉ scopes ⇒ 403; success attaches `:isaac/principal {:name :scopes}`. Both 401 and 403 feed `burst/record-unauthenticated!`.
- `isaac.http.auth/require-scope!` — throws a 403-mapped ex-info for handlers (hail `prompt` override etc.).
- Compat: when `:server :auth :token` is set and `:principals` lacks `:admin`, synthesize `{:admin {:hash (sha256 token) :scopes #{:*}}}` at config resolve time and log `:auth/legacy-token :warn` once per reload.
- Hot-reload: principals read through the s9e3 `cfg-fn` seam per request; a minted/revoked principal takes effect on the next request.
- `/cli` (cli-server handler) maps commands to `:cli/read` when the command's manifest entry is `:read-only` for that argv, else `:cli` — this leg lands in isaac-cli-server and may ride with child 3.

## Scenarios (features to plant @wip in isaac-http `features/http/auth.feature` — draft list)
1. a principal with the route's scope is accepted and the request carries its name (log line shows `:principal`)
2. a principal without the route's scope gets 403; an unknown bearer gets 401
3. a route without :scope requires admin
4. an expired principal is refused with 401
5. the legacy :server :auth :token still authenticates as admin and logs the deprecation once
6. minting a principal in config takes effect on the next request without a restart (reuses s9e3's reload steps)
7. the bearer never appears in any log line (existing log-scrub steps)

Step ledger at promotion (mirror s9e3's `features/http/auth_reload.feature` steps).


## Consumer note (planner, 2026-09-18) — Google push door (isaac-1jep)

The Google Workspace epic (isaac-bv1l) needs the `:isaac.http/identity` berth to accept a **request verifier**, `(fn [request] -> {:name … :scopes #{…}} | nil)`, not only a header→principal mapping: Pub/Sub push carries a Google-signed OIDC JWT, there is no shared secret to hash. The door route will declare `:scope :google/push`. Please keep the verifier shape in this bean's berth design (or tell isaac-1jep to add it).
