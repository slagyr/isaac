---
# isaac-ews9
title: Auth is on by default; enabled with no strategy is a boot error
status: todo
type: feature
priority: high
tags:
    - security
    - http
created_at: 2026-09-20T00:25:07Z
updated_at: 2026-09-20T00:25:14Z
parent: isaac-gym1
blocked_by:
    - isaac-pqqm
---

Repo: **isaac-http**. Blocked by the strategy chain bean (auth strategies). Micah, 2026-09-19: "I'm game with turning auth on by default."

## Why

`wrap-auth` computes `auth-on?` from whether config mentions principals, a token or an identity verifier. "Never configured" and "deliberately open" are therefore indistinguishable, so losing the config silently converts a locked server into an open one. That happened on zanebot the same evening: a module upgrade rewrote `isaac.edn`, the retired `:server :auth :token` went with it, and the server — public through Tailscale Funnel — served every route unauthenticated for about twenty minutes. Nothing warned, because that is a valid configuration today.

Inferring safety from the bind address does not work: yopp binds 127.0.0.1 and is still reachable across the tailnet because Tailscale serve proxies to it. The server cannot see what sits in front of it.

## Change

- Auth is **on** unless `:http :auth {:enabled false}`, mirroring `:http :burst {:enabled false}`.
- Enabled with no usable strategy is a **boot error**, not an open door. The message names the fix: `isaac http auth mint <name> --scopes '*'`.
- Disabled logs a warning every boot. **Log only — nothing on stderr** (Micah, explicit).
- No implicit token is ever minted. Principals are stored as SHA-256 hashes, so a secret minted at boot could only reach the operator through the log or config in the clear. `isaac init` mints the admin principal and prints the secret once, which keeps first run a single command.

## Acceptance

Scenarios (worker writes, isaac-http `features/`): a root with no auth config refuses a request with 401 rather than serving it; the boot error names the mint command; `:enabled false` serves unauthenticated and logs exactly one warning per boot with nothing on stderr; `isaac init` produces a config whose admin principal authenticates, with the secret printed once and only its hash stored.
