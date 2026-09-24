---
# isaac-x5kx
title: 'isaac http auth scopes: list the scopes installed modules declare on their routes; document scopes in the http README and mint --help'
status: todo
type: feature
priority: normal
created_at: 2026-09-24T13:37:58Z
updated_at: 2026-09-24T13:37:59Z
blocked_by:
    - isaac-p4oj
---

Micah, 2026-09-24: "Where is the list of scopes documented?" Nowhere. Scopes are whatever `:scope` each installed module puts on its `:isaac.http/route` entries (isaac-4o6r): today `hail/send` (POST /hail/send), `cli` (the /cli socket; read-only commands need no scope beyond authentication), `hooks` (webhook routes), plus `*` meaning everything and the legacy `:http :auth :token` acting as principal `admin` with `*`. The Google callback route is public.

## Design
- `isaac http auth scopes` — one row per scope: scope, the routes (method + path, module) that require it, and a one-line description from the route entry (`:scope-desc`, optional; fall back to the module description). Derived from the installed module index, so it is always current for that host.
- `isaac http auth mint --help` names the command and shows an example: `--scopes cli` for a laptop's remote CLI, `--scopes hail/send` for a dispatcher.
- `isaac http auth mint --scopes x` with a scope no installed route declares: refuse with the known list (typo protection), `--force` allows it.
- README section "Auth principals and scopes" in isaac-http: mint/rotate/revoke/list/scopes, the overlap window, the legacy token retirement path.

## Acceptance (features/cli/auth_principals.feature)
- [ ] `auth scopes` lists hail/send, cli, hooks with their routes on a root with those modules installed
- [ ] `auth mint x --scopes nope` refuses and names the known scopes; `--force` writes
- [ ] `bb ci` green

Repo scope: isaac-http (`cli.clj`, `auth_cli.clj`, README, features).
