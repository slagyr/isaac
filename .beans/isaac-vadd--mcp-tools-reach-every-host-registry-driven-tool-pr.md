---
# isaac-vadd
title: 'MCP tools reach every host: registry-driven tool-provider berth (server, prompt, acp)'
status: draft
type: feature
priority: high
tags:
    - mcp
    - agent
created_at: 2026-09-18T01:36:32Z
updated_at: 2026-09-18T01:36:32Z
parent: isaac-uhvt
---

Child of isaac-uhvt. Makes config-declared MCP servers actually reach turns in **every** host — server, `prompt`, `acp` — through one registry seam, with no per-host code.

## Finding (2026-09-17, plan)

`isaac.mcp.runtime/start!` is called by exactly one thing: the gherclj helper `feature-steps/isaac/mcp_steps.clj`. Nothing in isaac-http, isaac-agent, isaac-acp, or the `prompt` command references it.

| what | state at isaac-mcp `32300a2` (== modules.edn pin, deployed) |
|---|---|
| `:mcp` schema `:factory` | none — deliberately (qgtn: a factory rewrites validate errors to `mcp[:lens].command`); module_spec asserts its absence |
| `create-module` | bare `(module/module)`, no on-load |
| isaac-http `optional-registry-syms` | hail, hooks, cron only |
| qgtn / 6b5z acceptance | green because the step helper hand-starts the runtime |

Net: on zanebot an `mcp.<id>.command` entry validates and does nothing. No process is spawned, no `<id>__*` tool registers, no crew can be offered one. qgtn closed with "Reconfigurable `make` is still there for a later berth/registry bean" — this is that bean.

## Why not `start!` in each host

- Dependency direction: isaac-mcp depends on isaac-agent (registry) and isaac-http; `prompt` (agent) and `acp` cannot require `isaac.mcp.runtime` except via `requiring-resolve` string hacks in three repos.
- isaac-eqkb embeds `prompt`/`acp` in the server process (isaac-dqy9); the embedded host skips `:install!`, so per-command starts would stop firing exactly where they matter.
- isaac-3q4m `:isaac/component` is daemon-only; the standalone `prompt` (and `--local`) would still be blind.

## Design (proposed — confirm before promoting to todo)

**A tool-provider berth, resolved lazily by the registry.** The registry already has the one seam that runs in every host at turn time: `activate-missing-tool!` (registry.clj:54), called from `tool-definitions` (3-arity) and `execute`. Extend it for *dynamic* namespaces.

- isaac-agent manifest declares a manifest-only berth `:isaac.agent/tool-providers`, entry shape `{<provider-id> {:ensure! <sym>}}`.
- Registry: for each allow token (exact `:lens/catalog` **or glob `:lens/*`** — today globs skip activation) whose namespace has no registered tool and no `:isaac.agent/tools` module, call each provider `(ensure! ns-string module-index)`. A provider registers what it owns and returns the registered wire names, or nil.
- isaac-mcp contributes `{:mcp {:ensure! isaac.mcp.runtime/ensure-server!}}`: looks up `ns-string` in the `:mcp` table of the committed snapshot; connects once per process; registers `id__tool`; dead command → `:error :mcp/connect-failed`, nothing registered, not offered. Proposed: a failed server is not retried within 60s (the long-lived server must recover without a restart; a per-turn 30s spawn timeout is not acceptable).
- `McpRuntime` Reconfigurable stays (hot reload remains a later bean). `start!`/`stop!` stay as the whole-table entry points `ensure-server!` composes with.
- Cost: the first turn that allows a server pays the spawn + `tools/list` inside the turn. Accepted; log `:mcp/connected` with the elapsed ms.

## Work, per repo

| repo | change |
|---|---|
| isaac-agent | berth declaration in `resources/isaac-manifest.edn`; `activate-missing-tool!` consults providers (exact + glob tokens); spec for both token shapes and for "provider returns nil ⇒ not offered" |
| isaac-mcp | `ensure-server!`; manifest berth contribution; **delete the hand `start!` from `feature-steps/isaac/mcp_steps.clj`** — `the Isaac system is started` becomes load-config only, and every existing lifecycle/turn scenario must go green through the real seam (this is the forcing change); new `features/hosts.feature` (below); bump agent pin |
| isaac (modules.edn) | pin train after both land |

No host code changes. Does not touch isaac-1fwl's `prompt`/`acp` boot surfaces — land after 1fwl's agent pin to avoid churn, not blocked by it.

## Proposed scenarios (isaac-mcp `features/hosts.feature`, NOT yet written — approve first)

Reuse: `default Grover setup`, `config:`, `the crew "main" allows tools:`, `the following sessions exist:`, `the following model responses are queued:`, `isaac is run with {args}`, `the exit code is 0`, `session "…" has transcript matching:`, `stdin is:`, `the ACP commands are registered`. Fixture: `test-resources/marigold/lens_mcp.bb`. New steps: none.

1. **prompt command offers and invokes an MCP tool** — config `mcp.lens.command bb` + args, crew main allows `lens/*`, queued echo response `tool_call lens__catalog {"query":"marigold"}`; `When isaac is run with "prompt --crew main --session lens-run -m 'find marigold'"`; exit 0; transcript matching has a tool row `lens__catalog` whose result contains `marigold`.
2. **acp session invokes an MCP tool** — same config/allow/queue; stdin = initialize + session/new + session/prompt; `When isaac is run with "acp --session lens-acp"`; exit 0; transcript matching as above.
3. **existing lifecycle + turn scenarios pass with the helper no longer hand-starting the runtime** (no new scenario; the diff to `mcp_steps.clj` is the assertion).

## Acceptance (fill selectors once scenarios are committed `@wip`)

```
cd isaac-mcp && bb features features/hosts.feature
cd isaac-mcp && bb features features/lifecycle.feature features/turn.feature   # green with mcp_steps.clj no longer calling start!
cd isaac-agent && bb spec   # provider lookup: exact token, glob token, nil provider
cd isaac-mcp && bb ci
```

## Out of scope

- Hot reload of `:mcp` (on-config-change!) — later bean.
- HTTP/SSE transport, OAuth, MCP resources/prompts, ACP client `mcpServers` (isaac-zt4h).
- Adding MCP to isaac-http `optional-registry-syms` (server-only; superseded by the provider seam).
