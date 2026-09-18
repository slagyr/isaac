---
# isaac-tvcg
title: 'isaac remote: authenticate without the token in argv (--token-file / --token-env / ISAAC_REMOTE_TOKEN / pointer file); deprecate --token'
status: in-progress
type: feature
priority: high
tags:
    - cli
    - security
created_at: 2026-09-17T22:51:02Z
updated_at: 2026-09-18T00:55:09Z
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

- **`--token TOKEN` is deprecated, not removed** (decision 2026-09-17, Micah): it keeps working and prints `isaac remote: --token exposes the secret in the process list; use --token-file, --token-env, or ISAAC_REMOTE_TOKEN` on stderr. The warning never echoes the token. Removal is a later follow-up bean (one-time acceptance there, no permanent absence scenario).
- No token resolved ⇒ connect unauthenticated as today (server answers 401; error says which sources were tried).
- The token never appears in logs, error messages, the `start` frame, or `cache/cli.edn`.
- `isaac remote --help` documents the order.
- Resolver lives in its own ns (`isaac.cli-proxy.token`) so isaac-gar0's launcher routing calls it without the option parser.

## Scenarios (committed @wip — isaac-cli-proxy `features/remote.feature` @ 1bcc33e)

| line | scenario |
|------|----------|
| :79 | --token still authenticates but warns that it exposes the secret |
| :91 | a private token file supplies the bearer credential |
| :101 | a group- or world-readable token file is refused before connecting |
| :113 | a named environment variable supplies the bearer credential |
| :123 | an unset named environment variable is an error naming the variable |
| :133 | ISAAC_REMOTE_TOKEN supplies the bearer credential with no flag |
| :143 | the home config's remote token is used when the url matches |
| :157 | a literal token in a readable home config is refused |
| :172 | the home config's token is ignored for a different url |
| :185 | an explicit token file beats ISAAC_REMOTE_TOKEN |

Refusals exit 1 and make NO connection; no error or warning ever echoes a secret.

## Step ledger

| step | status |
|------|--------|
| a stub /cli server that replies with frames: | reuse (cli_proxy_steps) |
| isaac remote is run with {args} | reuse — **extend: substitute `${tmp}` (per-scenario temp dir) alongside `${stub.url}`** |
| the stub connection authorization is {expected} | reuse |
| environment variable {name} is {value} | reuse (foundation `isaac.config.config-steps` — overrides both `isaac.config.env` and `c3env`; the resolver must read env through one of those seams, not `System/getenv`) |
| the stderr contains {expected} / the stderr does not contain {expected} | reuse (foundation `cli_steps.clj:589,591`) |
| the exit code is {n} | reuse |
| **a file {path} with mode {mode} containing {content}** | **NEW — writes on the REAL fs under `${tmp}` and sets POSIX perms (mode is octal text)** |
| **the home config file with mode {mode} contains:** | **NEW — docstring EDN written to `<home>/.config/isaac.edn` under a bound `isaac.config.root/*user-home*` in `${tmp}`; substitutes `${stub.url}` but leaves other `${VAR}` text literal** |
| **the stub server received no connection** | **NEW — stub connection count is 0** |
| **the stub connection has no authorization** | **NEW — no Authorization header on the stub's connect** |

Four new steps + one substitution added to an existing step; none carries token knowledge in its text.

## Acceptance

```
cd isaac-cli-proxy && bb features features/remote.feature   # all 10 above green with @wip removed
bb spec && bb ci
```
- Unit specs for `isaac.cli-proxy.token`: resolution order; mode check (`& 077`); `${VAR}` substitution; trailing-newline trim on token files.
- `isaac remote --help` and README document the order and the deprecation.
- Module version bump; registry pin is a train step (planner).
- Field check after the train (verifier, zanebot): `ISAAC_REMOTE_TOKEN=… isaac remote wss://…/cli -- logs --follow &` then `ps -ww -o args= -p $!` shows no token.

## Worker checkpoint (2026-09-18, scrapper@isaac-work-1)

Done:
- Added `isaac.cli-proxy.token` with precedence, mode validation, token-file trimming, named/default env, and pointer-token resolution unit coverage.
- Extended remote CLI options/deprecation warning, added acceptance steps, removed the ten owned `@wip` tags, documented auth order, and bumped module to 0.1.4.
- Unit resolver/CLI specs are green: 10 examples, 0 failures, 12 assertions.
- Pushed `bean/isaac-tvcg` @ `e9087b3`.

Current RED:
- Full remote feature has one remaining pointer `${VAR}` failure: expected `Bearer pointer-secret`, got no Authorization. Literal-readable refusal and mismatched-URL scenarios are green in focused run.

Next:
- Resume at `spec/isaac/cli_proxy/cli_proxy_steps.clj:204` and `src/isaac/cli_proxy/token.clj:76`; inspect why feature env override `ZANE_TOK` is absent from the resolver map, then run all `features/remote.feature`, `bb spec`, and `bb ci`.
