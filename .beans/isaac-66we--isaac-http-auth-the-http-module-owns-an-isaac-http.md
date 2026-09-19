---
# isaac-66we
title: 'isaac http auth: the HTTP module owns an ''isaac http'' command; auth mint|rotate|revoke|list move off ''isaac server'''
status: completed
type: task
priority: high
tags:
    - security
    - http
created_at: 2026-09-19T02:20:39Z
updated_at: 2026-09-19T02:33:10Z
parent: isaac-gym1
---

## Why (Micah, 2026-09-18)

Foundation owns the process verbs: `isaac service …` (OS service manager) and `isaac server` (the daemon; `isaac.runner.cli` `cli-api/run :server`). Principals are HTTP-module state (`:http :auth :principals`, moved there by isaac-tdlz), so their CLI belongs to the HTTP module under its own verb: `isaac http auth mint|rotate|revoke|list`. `isaac http` starts nothing — Isaac starts things.

## What is wrong today (isaac-http origin/main 111fff1)

- `isaac.http.cli` registers a second `defmethod cli-api/run :server` on top of foundation's, and `wrap-runner-auth-list!` `alter-var-root`s foundation's `runner-cli/run-fn` to intercept `auth` before the daemon starts. Two owners of one verb, a monkey patch of foundation, and `parse-option-map` has to strip everything after `auth`.
- The manifest advertises `:isaac/cli {:server {:summary "Start the Isaac HTTP server, or mint/rotate/revoke/list principals"}}` — the HTTP module claims to start the server; on an installed host `isaac server auth list` boots the daemon instead (observed on zanebot 2026-09-19: BindException, args ignored).

## Change (isaac-http only)

- Manifest `:isaac/cli` gains `{:http {:usage "http <subcommand> [options]" :summary "HTTP module: manage principals (auth mint|rotate|revoke|list)" :namespace isaac.http.cli}}`; `cli-api/run :http`, `option-spec :http`, `subcommands :http` (the four `auth …` entries). `isaac http` with no subcommand or `--help` prints usage listing the auth subcommands and exits 0; it never starts a listener.
- Remove the `:server` `defmethod`s, `wrap-runner-auth-list!`, and the `auth` special-casing from `isaac.http.cli`. Remove `:server` from the manifest `:isaac/cli`. Foundation's `isaac server` is untouched (it stays the daemon until isaac-3q4m decides otherwise).
- `mint`/`rotate`/`revoke`/`list` behaviour, output, and config writes are unchanged — only the verb moves. Help text and the one-time-secret rule text follow the verb.
- Bump isaac-http version.

## Scenarios (committed `@wip` in isaac-http `099c99f`, branch `bean/isaac-66we`)

`features/cli/auth_principals.feature` (whole feature `@wip`): every `isaac is run with "server auth …"` became `"http auth …"`; description line follows; **new** scenario "isaac http --help lists the auth subcommands and starts nothing". `features/server/auth_audit.feature`: scenario "last-used is recorded for the principal and shown by auth list" (`@wip`) runs `http auth list`. `features/http/config.feature` "retired server auth token …" is about the config key and is NOT touched.

## Step ledger

| step | status |
|---|---|
| isaac is run with … / the stdout contains … / the exit code is … + every existing auth_principals / auth_audit step | reuse |

No new steps.

## Exceptions

- The `server auth` → `http auth` rewrite across `features/cli/auth_principals.feature` and `features/server/auth_audit.feature` (commit 099c99f) is the planner's own edit; the worker removes `@wip` only.

## Acceptance

Definition of done: `@wip` removed and

```
cd isaac-http && bb features features/cli/auth_principals.feature && bb features features/server/auth_audit.feature && bb ci
```

One-time checks (not scenarios): `isaac server auth list` is no longer recognised by the HTTP module (foundation's `isaac server` handles the verb alone; no `alter-var-root` of `runner-cli/run-fn` remains — `grep -n alter-var-root src/isaac/http/cli.clj` is empty); `isaac http --help` on a dev root prints usage without binding a port.

Downstream: isaac-2a2x's audit feature already re-pointed above; isaac-xo5p (rollout) must mint with `isaac http auth mint` once this lands. Registry pins are a train step.

Dispatched: hail 0d3bad85 2026-09-19T02:22:31Z (band isaac-work)


## Handoff (scrapper@isaac-work-2)

branch: bean/isaac-66we @ 0cbed75 (base origin/main@111fff1)

isaac-http: `isaac http auth mint|rotate|revoke|list`. Manifest `:isaac/cli` is `:http` + `:mcp-bridge` (no `:server`). Dropped `wrap-runner-auth-list!` / `alter-var-root` of `runner-cli/run-fn`. Version 0.1.18. @wip removed from auth_principals + auth_audit last-used scenario.

Gates: `ISAAC_GIT=1 bb spec` 163/0; `ISAAC_GIT=1 bb features` 93/0; focused auth_principals + auth_audit 21/0.


## Landed on main (2026-09-19)

main-sha: isaac-http d082206fa2a2e0099a892af863cb39016b7f3acd
main-sha: isaac-server d082206fa2a2e0099a892af863cb39016b7f3acd
