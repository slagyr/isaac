---
# isaac-gym1
title: 'Epic: per-principal scoped auth — named principals, hashed secrets in config, scopes on routes, mint-once tokens (replaces the single server token)'
status: todo
type: epic
priority: high
tags:
    - security
    - http
created_at: 2026-09-18T04:13:56Z
updated_at: 2026-09-18T04:13:56Z
---

## Why (Micah, 2026-09-17)

One bearer token (`:server :auth :token`) is root: whoever holds it can run any isaac command through `/cli` (in-process after isaac-eqkb), send hails with a `prompt` override ("make the model do whatever they want"), and change config. It lives in `orchestration/.env`, the CI secret, every zanebot worker's env (readable by any crew tool call), iPhone hook configs and editor ACP configs — so a leak is likely and invisible, and rotation is a fleet-wide fire drill.

## Decisions (2026-09-17, Micah)

- **Named principals with per-principal scopes** replace the single token. Revoke one client without touching the others; a leaked CI token can only do what CI does.
- **Config stores a HASH, never the secret.** The plaintext is shown exactly once, at mint ("the secret is a one-time event"). `isaac.edn`, the startup cache, `config get` and git are all safe to read. Rotation = mint a new hash.
- **Lives in isaac-http** (the HTTP module, local checkout `isaac-server`), not a new module: enforcement is `wrap-auth`, scopes ride on the `:isaac.http/route` berth HTTP already owns, and nothing else consumes principals. Identity SOURCES stay pluggable (bearer built in; e.g. Tailscale identity headers later via an `:isaac.http/identity` berth).
- **Backward compatible**: the existing `:server :auth :token` is principal `admin` with scopes `#{:*}` plus a deprecation warning; nothing breaks on deploy.
- **Hot-reload, no restart** — reuses isaac-s9e3's `cfg-fn` seam ([[no-service-restarts]]).

## Model

```clojure
:server {:auth {:principals {:admin     {:hash "sha256:…" :scopes #{:*}}
                             :ci        {:hash "sha256:…" :scopes #{:hail/send} :expires "2026-12-01"}
                             :iphone    {:hash "sha256:…" :scopes #{:hooks}}
                             :micah-mbp {:hash "sha256:…" :scopes #{:cli :acp :hail/send}}}}}
```

- Route berth entries gain `:scope` (`{:method :post :path "/hail/send" :handler … :scope :hail/send}`); a route without `:scope` requires `:*` (admin) — secure by default.
- `wrap-auth` resolves the bearer → principal (constant-time hash compare), rejects expired, attaches `:isaac/principal {:name :scopes}` to the request, checks the route scope. 401 = no/unknown token; 403 = known principal, insufficient scope (both feed burst control).
- Handlers needing finer control call `(isaac.http.auth/require-scope! request :hail/prompt-override)`.
- `/cli` reuses isaac-kjzq's `:read-only` hint: read-only commands need `:cli/read`, others `:cli`; `:local-only` already never runs remotely.
- Every request log line and every hail record carries `:principal`.

## Children (in order)
1. **isaac-bzgw** — Principals + scopes in wrap-auth, route-berth `:scope`, request principal, `require-scope!`, admin compat, hot-reload (isaac-http).
2. **isaac-auie** — `isaac server auth mint|rotate|revoke|list` — mint prints the secret once, writes the hash via config mutation (hot-reloads); list shows scopes/expiry/last-used (isaac-http).
3. **isaac-4o6r** — Modules declare scopes on their routes: hail (`:hail/send`; `prompt` override → `:hail/prompt-override`), cli-server (`:cli` / `:cli/read`), acp (`:acp`), hooks (`:hooks`), mcp (`:mcp`), episodes/others as found — pin bumps.
4. **isaac-2a2x** — Audit + alerts: `:principal` on request logs and hail records; Discord alert on 401 bursts, first use of a principal, use of an expired/revoked token; last-used persisted for `list`.
5. **isaac-xo5p** — Rollout on zanebot (ops): mint `ci`, `iphone`, laptops, one per worker session (hail-only), `plan`; swap the secrets where they live; then retire `:server :auth :token` (one-time acceptance: no client still presents the admin token — read from the audit log).

## Open (defaults chosen; say if wrong)
- CI token expiry: default NONE unless `--expires` is given; the rollout mints `ci` with a 90-day expiry and the alert in child 4 fires 7 days before any expiry.
- Scope vocabulary is flat keywords declared by routes; `:*` is the only wildcard. No hierarchy until a real need appears.
- Funnel exposure (what actually needs to be public) is a separate question — not in this epic.
