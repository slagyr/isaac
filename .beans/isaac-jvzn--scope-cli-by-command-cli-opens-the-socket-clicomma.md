---
# isaac-jvzn
title: 'Scope /cli by command: cli opens the socket, cli/<command> runs that command'
status: draft
type: feature
priority: high
tags:
    - security
created_at: 2026-09-26T02:39:37Z
updated_at: 2026-09-26T02:46:28Z
parent: isaac-gym1
---

`GET /cli` declares no `:scope`. A route with no scope requires `:*`, so any token that can open the remote CLI is an admin token for every other route. Yopp and Zanebot publish that listener through Tailscale Funnel.

isaac-4o6r specified `:scope :cli` on the route and `:cli/read` for read-only commands, and left four `@wip` scenarios in `isaac-cli-server/features/cli/endpoint.feature`. The manifest entry still has no `:scope`, dispatch never checks a principal, and the step `the /cli client is principal {name} with scopes {scopes}` was never defined. Delete those four scenarios. This bean does not implement them.

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
