---
# isaac-auie
title: isaac server auth mint|rotate|revoke|list — secret printed once, hash written to config (hot-reload), overlap rotation
status: draft
type: feature
priority: high
tags:
    - security
created_at: 2026-09-18T04:13:56Z
updated_at: 2026-09-18T04:13:56Z
parent: isaac-gym1
blocked_by:
    - isaac-bzgw
---

Child 2 of isaac-gym1 (principals epic). Repo: isaac-http. Blocked by child 1.

## Work
`isaac server auth …` (contributed via `:isaac/cli`; `server` is already isaac-http's command):
- `mint <name> --scopes a,b[,…] [--expires YYYY-MM-DD]` — generates 32 random bytes (base64url), prints the plaintext ONCE to stdout (nothing else on stdout so it can be captured), writes `{:hash "sha256:…" :scopes … :expires …}` under `:server :auth :principals <name>` through the config mutation API (hot-reloads; formatted per isaac-2nkg when that lands). Refuses to overwrite an existing name (use rotate).
- `rotate <name> [--overlap 24h]` — mints a new secret for the name; with `--overlap` the old hash stays valid as `<name>@prev` until the window ends (child 1 honours `:expires` on it), so clients update at their own pace.
- `revoke <name>` — removes the principal (and any `@prev`).
- `list` — table: name, scopes, expires, last-used (from child 4's audit store; "never" until then).
- Runs local-only in spirit (admin scope over the pipe; `:read-only #{"list"}` for kjzq).
- The plaintext never touches logs, the cache, or the transcript when run through a crew (stdout of a tool call IS the transcript — document: mint from a shell, not from a crew).

## Scenarios (draft; features/cli/auth_principals.feature)
1. mint prints exactly one line (the secret) and the config gains the hash, never the secret (grep the file for the printed value)
2. mint of an existing name is refused; rotate replaces the hash and (with --overlap) keeps the previous one valid until expiry
3. revoke removes the principal and the next request with its token is 401
4. list shows scopes and expiry; the secret column does not exist
5. the minted token authenticates against a running server without restart (integration, reuses child 1's steps)
