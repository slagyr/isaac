---
# isaac-bzgw
title: 'isaac-http: principals + scopes in wrap-auth; :scope on the route berth; request principal; require-scope!; legacy token = admin; hot-reload'
status: in-progress
type: feature
priority: high
tags:
    - security
created_at: 2026-09-18T04:13:56Z
updated_at: 2026-09-18T05:23:55Z
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



## Scenarios (committed @wip — isaac-http `features/server/principals.feature` @ fa2daab)

| line | scenario |
|------|----------|
| :20 | a principal holding the route's scope reaches the handler and is named in the request log |
| :31 | a known principal without the route's scope is refused with 403 |
| :42 | an unknown bearer is refused with 401 and never logged |
| :57 | a route without a declared scope requires admin |
| :68 | an expired principal is refused with 401 |
| :79 | the config holds a hash, never the secret |
| :85 | the legacy :server :auth :token still authenticates as admin and warns once |
| :104 | a principal added to config takes effect on the next request without a restart |
| :116 | a revoked principal is refused on the next request without a restart |
| :128 | a handler can require a finer scope than its route |
| :141 | 401 and 403 both count toward burst control |

Log events this bean defines: `:http/request` (info; `:principal :uri :status`), `:auth/refused` (`:principal` nil|name, `:reason :unknown|:expired|:scope`), `:auth/legacy-token` (warn, once per config load). If `:http/request` already exists under another name, keep the existing name and fix the scenarios' `event` column (allowed as a one-line planner exception — record it here).

## Step ledger

| step | status |
|------|--------|
| an Isaac root at … / config: / the Isaac server is started / config is updated: / the isaac config is reloaded | reuse |
| the client sends GET {path} with header {header} (+ `… N times`) | reuse |
| the response status is {n} / the response header … matches … | reuse |
| the log has entries matching: / the log has no entries matching: | reuse |
| the config file {path} does not contain {text} | reuse (foundation) |
| **the isaac config path {path} matches {regex}** | **NEW — Then-side matcher; the existing `the isaac config path … is …` is a Given that SETS a value** |
| **principal {name} is configured with secret {secret} and scopes {scopes}** (+ **… expiring {date}**) | **NEW — computes sha256 of the secret and writes `server.auth.principals.<name>` {:hash :scopes (:expires)} into the root config; runnable before or after server start** |
| **principal {name} is removed from config** | **NEW** |
| **a fixture route {method} {path} requires scope {scope}** / **… declares no scope** / **… requires scope {scope} and its handler requires {scope2}** | **NEW — registers a 200-handler route through the :isaac.http/route berth path (with/without :scope; the third variant's handler calls `isaac.http.auth/require-scope!`)** |
| **the log has exactly {n} entries matching:** | **NEW — count variant of the existing matcher** |

Five new step families. **Where they live (Micah, 2026-09-17):** steps 1–3 (principal configured/removed, fixture route) are HTTP-specific → isaac-http `spec/` step ns. Steps 4–5 (`the log has exactly {n} entries matching:`, `the isaac config path {path} matches {regex}`) are generic → **isaac-foundation `spec-support`** next to their siblings (`cli_steps.clj` log matchers, `config_steps.clj` config-path Given), released with a foundation spec-support bump and pinned from isaac-http (dev-local while iterating). Same shape as tvcg/gar0's placeholder-substitution steps.

## Acceptance
```
cd isaac-server && bb features features/server/principals.feature   # 11 green, @wip removed
bb features features/server/auth.feature features/server/burst.feature   # legacy + burst unchanged
bb spec && bb ci
```
Note for the worker: on a fresh clone the current deps pin for isaac-agent (`b6284e42…`) is not fetchable from GitHub (the commit was squashed away); CI's gitlibs cache still has it. If `bb` fails "Commit not found", bump the agent pin in deps.edn + bb.edn to agent main (`0e804c0` or newer) as the first commit of this bean and note any spec follow-ups.

Dispatched: hail 498cd641 2026-09-18T05:20Z (band isaac-work)
