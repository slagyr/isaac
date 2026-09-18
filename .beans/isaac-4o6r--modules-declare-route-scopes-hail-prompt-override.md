---
# isaac-4o6r
title: 'Modules declare route scopes: hail (+prompt-override), cli (+cli/read via :read-only), acp, hooks, mcp'
status: in-progress
type: feature
priority: high
tags:
    - security
created_at: 2026-09-18T04:13:56Z
updated_at: 2026-09-18T14:00:57Z
parent: isaac-gym1
blocked_by:
    - isaac-bzgw
---

Child 3 of isaac-gym1 (principals epic). Blocked by child 1 (pin bumps to the isaac-http sha that carries `:scope`).

| repo | route(s) | scope | extra |
|---|---|---|---|
| isaac-hail | `POST /hail/send` | `:hail/send` | handler: `prompt` override (band template override) requires `:hail/prompt-override` via `require-scope!`; `reply_to`/session-direct frequencies allowed under `:hail/send` |
| isaac-cli-server | `GET /cli` | `:cli` | handler: command whose manifest is `:read-only` for this argv needs only `:cli/read` (kjzq hint); mismatch ⇒ stderr + exit 77 frame, no execution |
| isaac-acp | `/acp` (if still routed) | `:acp` | |
| isaac-hooks | hook routes | `:hooks` | |
| isaac-mcp | MCP routes | `:mcp` | |
| isaac-http | health/root | none ⇒ admin, except an explicit unauthenticated `/health` if one exists today (keep behaviour) | |

Each repo: manifest `:scope` on the route entry, one scenario per route ("a principal scoped X reaches /route; one without gets 403"), pin bump, version bump. Registry pins are a train step.



## Scenarios (committed @wip)

| repo @ sha | scenario |
|---|---|
| isaac-hail `features/http.feature` @ 2a905c1 | a principal scoped hail/send can send a band hail |
| | a principal without hail/send is refused with 403 and nothing is persisted |
| | overriding a band's prompt requires hail/prompt-override |
| | a principal holding hail/prompt-override may override the prompt |
| | a session-direct hail with a prompt is ordinary hail/send |
| | the legacy admin token still sends hails with a prompt override |
| isaac-hooks `features/hooks.feature` @ 19e3d36 | a principal scoped hooks can fire a configured hook |
| | a principal without hooks is refused with 403 and no turn starts |
| isaac-cli-server `features/cli/endpoint.feature` @ 3a77125 | a principal scoped cli/read may run a read-only command |
| | a principal scoped cli/read is refused a mutating command before it runs (exit 77, `:cli/refused-scope`) |
| | read-only per subcommand follows the manifest hint |
| | a principal scoped cli runs everything (`:cli/command-started` carries `:principal`) |

Not scenario'd (declare the scope on the manifest entry + one spec each): isaac-claude-code `POST /claude/turns/:id` → `:mcp`; isaac-http's own `/acp` and `/status` (`/status` stays whatever it is today; if it is unauthenticated for health checks, give it `:scope :public` handled in child 1 as "no auth" — decide with the worker, record here).

Hail record gains `:principal` (from `:isaac/principal` on the request; `admin` for the legacy token). Session-direct hails carry `:prompt` by necessity — only the BAND-template override is gated.

## Step ledger

| step | status |
|------|--------|
| default Grover setup / a POST request is made to …: / the response status is … / the sole pending hail EDN contains: / the hail router ticks / the sole delivery hail EDN contains: / the following sessions exist: / the isaac EDN file … exists with: | reuse (hail) |
| session … has transcript matching: / the cli-server handler with the fixture commands registered / a /cli client sends start with argv … / the handler sends frames: / the cli log has entries matching: | reuse |
| principal … is configured with secret … and scopes … | reuse — **must live in isaac-http `spec-support` (published), not `spec/`, so hail/hooks/cli-server features can load it via the `isaac.**-steps` glob** |
| **there are no pending hails** | **NEW (hail)** |
| **session {name} does not exist** | **NEW (hooks/agent session steps)** |
| **the /cli client is principal {name} with scopes {scopes}** | **NEW (cli-server) — attaches `:isaac/principal` to the upgrade request the handler sees** |

Three new steps.

## Acceptance
```
cd isaac-hail && bb features features/http.feature && bb ci
cd isaac-hooks && bb features && bb ci
cd isaac-cli-server && bb features && bb ci
cd isaac-claude-code && bb spec && bb ci
```
Each repo: pin bump to the isaac-http sha carrying `:scope` + `:isaac/principal`; version bump. Registry pins are a train step; all five must ride ONE train with isaac-http (a route without `:scope` requires admin, so shipping http before the modules would lock out scoped principals — the legacy admin token keeps working either way).

Dispatched: hail 1f501242 2026-09-18T06:14Z (band isaac-work)
