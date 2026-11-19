---
# isaac-p9e3
title: MCP servers connect when Isaac starts, so a crew's first turn has their tools
status: completed
type: bug
priority: normal
created_at: 2026-11-19T14:59:06Z
updated_at: 2026-11-19T15:10:42Z
---

Repo: **isaac-mcp** (`src/isaac/tool/mcp/runtime.clj`, `resources/isaac-manifest.edn`, `feature-steps/isaac/tool/mcp/mcp_steps.clj`).

## What is wrong

A crew's first turn after a server restart gets none of its MCP tools.
On yopp, 2026-11-19: the host rebooted at 09:12 UTC. The 06:00 morning
update (12:00 UTC) was crew yopp's first turn since. It resolved its tools
at 12:00:14, which started the Linear connect in the background. Linear
connected at 12:00:24, but the turn had no `linear__*` tools, so the
report went out without its Linear sections. 11-17's "Linear data was
unavailable" report probably had the same cause.

Why: isaac-vadd made servers start only when a turn's policy names their
namespace (`ensure-server!` via `:isaac.agent/tool-providers`), and
isaac-aswr made turns never wait on that connect. `start!` and
`McpRuntime` (`on-load` → `start!`) were meant to connect every server at
boot, but nothing creates `McpRuntime`: `make` has no caller, and
`module_spec` requires no `:factory` on the `:mcp` schema.

## Fix

When Isaac starts, kick off a background connect for every configured
MCP server, and do the same on config changes, as `McpRuntime` already
does. The turn path still never waits (isaac-aswr). Wire it through a
production boot hook (module activation or a reconfigurable that is
actually created). Keep the dotted config error keys that module_spec
protects.

Make "the Isaac system is started" in `mcp_steps.clj` go through that
same boot hook, never calling `start!` by hand.

## Acceptance

- `features/lifecycle.feature` "configured servers connect when Isaac starts, before any turn" passes.
- The rest of `isaac-mcp` features and specs still pass.
- `bb ci` green.

## Exceptions

- The worker may rewrite the description paragraph at the top of
  `features/lifecycle.feature` ("Configured MCP servers start over stdio
  the first time a turn allows their namespace…") to say servers connect
  at boot. No scenario steps change.
- The worker may update the namespace docstring of `mcp_steps.clj` and
  the "the Isaac system is started" step to use the boot hook.

feature-baseline: isaac-mcp 95a62a55ebfaf9c683ed81a07a42c904d257ece7
feature-blob: isaac-mcp features/lifecycle.feature 49e235e674d63e9417f6c5d098f295c064c306a9 37


## Work checkpoint (2026-11-19)
Done: boot MCP via :isaac.config/component factory, feature boot uses production reconciler, config reload unit coverage; focused acceptance green; gate PASS. Branch bean/isaac-p9e3 pushed at 5e29de3.
Next: bb ci intermittent failure: lifecycle.feature two-server log order can be skybeam then lens. Fix stable boot order without blocking turn; resume src/isaac/tool/mcp/runtime.clj:186 (connect-async!) / :261 (start!), verify full bb ci then re-run gate and land.
Last run: bb spec spec/isaac/tool/mcp/runtime_spec.clj green (32 examples); prior bb ci red from nondeterministic log ordering.


## Landed on main (2026-11-19)

main-sha: isaac-mcp bfa318c0a0c9b1e98a454700f036a0f484b99d17

Verification: bb ci green (49 specs / 15 features), bean-gate PASS on main. Boot and reload use the declared config component; boot connects are concurrent and catalogs publish in server-id order.
