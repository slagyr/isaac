---
# isaac-tvcg
title: 'isaac remote: authenticate without the token in argv (--token-file / --token-env / ISAAC_REMOTE_TOKEN / pointer file); deprecate --token'
status: draft
type: feature
priority: high
tags:
    - cli
    - security
created_at: 2026-09-17T22:51:02Z
updated_at: 2026-09-17T22:51:02Z
blocking:
    - isaac-gar0
---

Micah (2026-09-17): "We need a way to run the remote CLI using an auth token … without exposing the token in the ps."

## Problem

`isaac remote <url> --token TOKEN -- <cmd>` is the ONLY way to authenticate today (isaac-cli-proxy `cli.clj:18,31` → `proxy.clj:21` bearer header). Argv is world-readable: any local user sees the server's bearer token in `ps`, and it lands in shell history and in editor ACP launch configs. That token is the zanebot server token — the whole server is public on 443.

## Design (isaac-cli-proxy; standalone, shippable before the isaac-eqkb epic; isaac-gar0 reuses the resolver)

Token resolution, first hit wins — none of them put the secret in argv:

1. `--token-file PATH` — read + trim. The file must not be group/world readable (mode & 077 == 0); otherwise refuse, naming the `chmod 600`.
2. `--token-env VAR` — read the named variable; unset/blank ⇒ error naming VAR.
3. `ISAAC_REMOTE_TOKEN` — default env var, no flag needed.
4. `~/.config/isaac.edn` `:cli :remote {:url … :token …}` when the URL given matches `:url` (or none was given — isaac-gar0). `${VAR}` is substituted; a LITERAL token requires the pointer file be 0600 (same check as 1).

(Env is visible only to the same uid/root — `ps e`, `/proc/<pid>/environ` — unlike argv. The file forms are strongest and are what editor/launchd configs should use.)

- **`--token TOKEN` is deprecated**: still works for one release, prints `isaac remote: --token exposes the secret in the process list; use --token-file, --token-env, or ISAAC_REMOTE_TOKEN` on stderr. Removal is a follow-up (one-time acceptance there, no permanent absence scenario).
- No token resolved ⇒ connect unauthenticated as today (server answers 401; error says which sources were tried).
- The token never appears in logs, error messages, the `start` frame, or `cache/cli.edn`.
- `isaac remote --help` documents the order.
- Resolver lives in its own ns (`isaac.cli-proxy.token`) so isaac-gar0's launcher routing calls it without the option parser.

## Scenarios (isaac-cli-proxy `features/remote.feature`, to be planted @wip at promotion)

1. a token file supplies the bearer credential — `--token-file ${tmp}/tok` (0600) ⇒ authorization is "Bearer file-secret".
2. a group/world-readable token file is refused — 0644 ⇒ nonzero exit, stderr contains "chmod 600", no connection made.
3. a named env var supplies the bearer credential — `--token-env MY_TOK`.
4. an unset named env var is an error naming the variable.
5. ISAAC_REMOTE_TOKEN supplies the bearer credential with no flag.
6. the pointer file's remote token is used when the url matches (`${VAR}` form in a 0644 file).
7. a literal pointer-file token in a 0644 file is refused.
8. precedence: `--token-file` beats `ISAAC_REMOTE_TOKEN`.
9. `--token` still authenticates and warns on stderr (existing scenario :68 gains the stderr assertion).

## Step ledger

| step | status |
|------|--------|
| a stub /cli server that replies with frames: | reuse |
| isaac remote is run with {args} | reuse |
| the stub connection authorization is {expected} | reuse |
| the exit code is {n} | reuse |
| the stderr contains {text} | reuse (verify phrasing with `gherclj match`) |
| **a file {path} with mode {mode} containing {content}** | **NEW — writes under the scenario tmp dir, sets POSIX perms** |
| **the environment variable {name} is {value}** | **NEW — binds the proxy's env seam (`c3env` override), not the real process env** |
| **the home config file contains:** | **NEW — writes `~/.config/isaac.edn` under a bound `root/*user-home*`; takes a mode column/arg for scenario 7** |
| **the stub server received no connection** | **NEW — asserts the stub's connection count is 0** |

Four new steps; all generic (no token knowledge in the step text).

## Acceptance

```
cd isaac-cli-proxy && bb features features/remote.feature && bb spec && bb ci
```
Field (after the train): on zanebot `ISAAC_REMOTE_TOKEN=… isaac remote wss://…/cli -- version &` then `ps -ww -o args= -p $!` shows no token.
Docs: README + `remote --help`. Module version bump + registry pin (train step).
