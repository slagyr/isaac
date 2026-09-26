---
# isaac-jvzn
title: 'Scope /cli by command: cli opens the socket, cli/<command> runs that command'
status: in-progress
type: feature
priority: high
tags:
    - security
created_at: 2026-09-26T02:39:37Z
updated_at: 2026-09-26T02:54:50Z
parent: isaac-gym1
---

`GET /cli` declares no `:scope`. A route with no scope requires `:*`, so any token that can open the remote CLI is an admin token for every other route. Yopp and Zanebot publish that listener through Tailscale Funnel.

On main, `GET /cli` already declares `:scope :cli`, and the command filter allows `:*`, `:cli`, and `:cli/read` when the command is marked read-only. The step `the /cli client is principal {name} with scopes {scopes}` already exists. The four live cli/read scenarios were removed in isaac-cli-server 7d0e966. Do not put them back. No new steps.

## Decision (2026-09-25, Micah)

Scope vocabulary for the remote CLI is the namespace `cli`, matching the existing keyword form (`hail/send`, `google/push`).

- `:cli` opens `GET /cli` and may run every hosted command.
- `:cli/<command>` opens `GET /cli` and may run that one top-level command. The command id is the registry name: the first argv word after `--root` is stripped (`acp` → `cli/acp`, `logs` → `cli/logs`, `http auth mint` → `cli/http`). Subcommands are not separate scopes.
- `:*` still opens the route and runs every hosted command.
- A sibling does not grant a sibling. `:cli/acp` does not run `logs`.
- `:local-only` stays refused (`server`, `service`, `modules`, `remote`, exit 2 from the host). A scope cannot override it.
- Empty argv / `--help` stays usage for any principal who was allowed to open the socket. It does not run a command.

This is the hierarchy isaac-gym1 deferred ("no hierarchy until a real need"). It is one-directional. Do not implement it by teaching `authorized?` that `:cli/acp` means `:cli` for every later check: the command filter would then let a single-command token run everything.

## Door (`isaac-http`)

`authorized?` stays exact for a required scope that already has a namespace (`:cli/acp`, `:hail/send`): the principal must hold that keyword or `:*`.

When the required scope has no namespace (`:cli`, `:hooks`), it is also satisfied by any held scope whose namespace is that name. So `:cli/acp` and `:cli/logs` pass a route scoped `:cli`, and `:hail/send` does not. Holding `:cli` does not satisfy a required `:cli/acp`. Holding `:cli/acp` does not satisfy a required `:cli/logs`.

No other route's meaning changes today. `:hail/send`, `:hooks`, `:google/push`, and `:google/oauth-callback` keep their current holders working. A future token `:hooks/foo` would pass a route scoped `:hooks`; that is the same rule.

## Command filter (`isaac-cli-server`)

Manifest route entry:

```clojure
{:method :get :path "/cli" :handler isaac.cli-server.ws/handler :scope :cli}
```

`ws/handler` closes over `:isaac/principal` from the upgrade request and passes it into dispatch on `start`.

Before a hosted command runs, allow it when the principal holds `:*`, exact `:cli`, or exact `:cli/<command>`. Otherwise refuse before `run-embedded`: stderr that names the required scope, exit 77, log `:warn :cli/refused-scope` with `:principal` and `:argv`. Do not start the command.

When the request has no `:isaac/principal`, do not filter. That is the existing handler-suite path. Production auth still sits in `wrap-auth` in front of the handler.

Land the `isaac-http` door rule first, then the route scope. `:*` keeps working in either order. A `:cli/acp` token works only after both.

## Out of scope

Minting a Yopp or Zanebot principal, installing `isaac.cli-server` on Yopp, and rotating the live admin token. isaac-x5kx lists route-declared scopes; once this lands, that catalog also has to name `cli/<command>` for each hosted command, or mint's typo check will refuse a legitimate laptop token.

## Scenario plan (not yet written)

isaac-http, principals feature:

1. A principal holding `cli/acp` reaches a route that requires `cli`.
2. A principal holding only `hail/send` is refused that route with 403.
3. A principal holding `cli` reaches a route that requires `cli`.
4. A route that requires `cli/acp` admits `cli/acp` and `*`, and refuses `cli` and `cli/logs`.

isaac-cli-server, `features/cli/endpoint.feature`. `fx-print`, `fx-echo`, and `fx-local` are fixture commands the test step registers. They are not product commands. The scope name is the command's registry name, the same rule as production `acp` and `logs`.

5. Delete the four `@wip` isaac-4o6r scenarios. They require `:cli/read`.
6. A principal holding only `cli/fx-print` runs `fx-print`.
7. That principal is refused `fx-echo` before it runs (exit 77, no stdout from the command).
8. A principal holding `cli` runs `fx-echo`.
9. A principal holding `*` runs `fx-echo`.
10. A principal holding `cli` is still refused `fx-local` (local-only).
11. A principal holding only `cli/fx-print` may send empty argv and get usage.

Promote to `todo` only after those scenarios are committed `@wip` and `bb bean-gate baseline` has frozen them.

## Decision (2026-09-25, Micah) — no read/write scope

`:cli/read` is out. Classifying every command and subcommand as read or write is not part of this bean. The `:read-only` manifest hint stays what isaac-kjzq uses it for: while a restart is pending, a command marked read-only may still run. No production command sets that hint. Only the cli-server fixtures `fx-read` and `fx-multi` do. Auth scopes do not consult it.

## Acceptance

```
cd isaac-http && bb features features/server/principals.feature:150 features/server/principals.feature:161 features/server/principals.feature:172 features/server/principals.feature:180 features/server/principals.feature:188 features/server/principals.feature:196 features/server/principals.feature:207
cd isaac-cli-server && bb features features/cli/endpoint.feature:170 features/cli/endpoint.feature:183 features/cli/endpoint.feature:197 features/cli/endpoint.feature:210 features/cli/endpoint.feature:220 features/cli/endpoint.feature:230
```

Remove `@wip` as each scenario passes. Land the isaac-http door rule first (`authorized?`: an un-namespaced required scope is also satisfied by a held scope in that namespace; a namespaced required scope stays exact), then the cli-server filter (`:cli/<command>` instead of `:cli/read`; empty argv stays usage).

feature-baseline: isaac-http ff057d4188f7fa7681021b32e6229286c82629e1
feature-baseline: isaac-cli-server 7d0e966151a3296d71cbd7bfa1954e48d49157dd
feature-blob: isaac-http features/server/principals.feature e4325df7f42f157e7be4ecf574ff3563519eb816 150,161,172,180,188,196,207
feature-blob: isaac-cli-server features/cli/endpoint.feature f89f775b8025c2f3b7d7b59e6a1f2c3dfce19dfe 170,183,197,210,220,230

## Worker conflict (2026-09-25)

Implementation is committed on `bean/isaac-jvzn` in isaac-http and isaac-cli-server. Acceptance scenarios pass (7/7 and 6/6), `bb bean-gate verify isaac-jvzn --dir isaac-http=../isaac-http-jvzn --dir isaac-cli-server=../isaac-cli-server-jvzn` passes. isaac-http `bb ci` passes (189 specs, 117 features). CLI-server `bb features` passes (20 scenarios), but `bb ci` fails on an unrelated preexisting spec: `dispatch replays buffered frames after attach and renders them once`, `spec/isaac/cli_server/dispatch_spec.clj:150`: `Expected: "second\n", got: ""`. This fails consistently on an isolated detached `origin/main` checkout (`bb spec spec/isaac/cli_server/dispatch_spec.clj:126`, 1 failure) as well as the bean branch; the trace on the bean branch showed an exit frame without a stdout frame after attach. The scope change does not touch stream buffering; need planner disposition on repairing this preexisting red suite or revising landing criteria. Neither repo landed on main; bean remains in progress.


## Planner adjustment (2026-09-26, prowl@isaac-plan) — land; the attach-stdout spec is not this bean

Disposition: do not fix `dispatch_spec.clj:150` under isaac-jvzn, and do not hold the landing for it.

The worker reproduced it on isolated detached `origin/main` (`bb spec spec/isaac/cli_server/dispatch_spec.clj:126`, expected `"second\n"`, got `""`). The scope change does not touch stream buffering. Acceptance is green (http 7/7, cli-server 6/6) and `bb bean-gate verify` passed. A red suite that is red on main is not a reason to refuse a landing that does not touch it.

### Worker now

1. Land isaac-http first, then isaac-cli-server, as the bean already says. Record both `main-sha` lines.
2. `bb ci` on cli-server may still fail `dispatch replays buffered frames after attach and renders them once` (`spec/isaac/cli_server/dispatch_spec.clj:150`). That failure is authorized to remain. Do not edit `dispatch_spec.clj` or the attach/stream path on this branch. If `bb ci` fails on anything else, stop and report it.
3. Do not absorb the attach-stdout bug into this bean. It is filed separately as a draft.

This note resets the verify-fail counter.

## Landed on main (2026-09-25)

main-sha: isaac-http 8e01658366f6edddab9ff1fa5b857d12231add3b
main-sha: isaac-cli-server 0597c4ba717bc0eb56a6badd0805a738463d1726

isaac-http `bb ci` passed; cli-server `bb features` passed (20 examples). cli-server `bb ci` failed solely at the preexisting `dispatch_spec.clj:150` attach-stdout failure authorized by the planner. Gate passed on both landed main commits. The attach-stdout bug is tracked separately in draft isaac-59kb.
