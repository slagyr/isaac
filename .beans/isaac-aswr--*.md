
## Conflict (scrapper@isaac-work-1, 2026-09-24)

Runtime work is done and green on isaac-mcp `bean/isaac-aswr` (65cd3ce):
background connect via `connect-async!` (one future per server, generation token
so stop!/reload abandon in-flight connects and register nothing), hold backoff
60s→2m→4m…15m cap with reset on success, `:mcp/connect-held` once per hold,
start! non-blocking, `await-connects!` for tests/hosts, call-server! still
reconnects on demand bounded by the tool timeout. `bb spec` 40/0, `bb lint` 0/0,
manifest version 0.1.1→0.1.2 (the version lives in the manifest; nothing else in it changed).
isaac.tool.registry/tool-providers read: no change needed.

**Blocker: `bb features` goes 13 examples / 8 failures** (it's 13/0 on main).
Nothing in production calls `start!` (the only entry is the `ensure-server!`
tool-provider). So under the ruling the first turn that allows `lens/*` gets
no MCP tools, and the tools arrive on the next turn. turn.feature and
lifecycle.feature (catalog.feature as well) say the *first* turn after "the Isaac system
is started" offers and invokes `lens__catalog` ("unknown tool: lens__catalog" /
prompt tools `#{}`). Those scenarios are the contract, and they contradict the
design. I didn't make the step prewarm servers by hand: that would hide
production behavior, and the step ns doc says nothing starts the runtime by hand (isaac-vadd).
The planner needs to pick one: (a) rewrite the scenarios so a warm-up turn
comes first, or add a "the MCP servers have connected" step that calls
`isaac.mcp.runtime/await-connects!` after a first turn; or (b) have boot call
`start!` (wire McpRuntime as the :mcp config factory) so the step can await
the boot connects.
