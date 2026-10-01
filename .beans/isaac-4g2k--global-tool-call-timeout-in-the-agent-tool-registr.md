---
# isaac-4g2k
title: Global tool-call timeout in the agent tool registry
status: in-progress
type: feature
priority: normal
created_at: 2026-10-01T17:23:56Z
updated_at: 2026-10-01T18:22:52Z
---

Micah, 2026-10-01. Only `exec__run` has a timeout (30s default, per-call `timeout` arg). File tools (`fs__read`, `fs__glob`, `fs__grep`) and others have none: on zanebot a read of an iCloud-evicted (dataless) file and an unbounded home-directory glob each held a cron turn for ~7 hours (tempest-vault-sync 25f87b25, heartbeat 9bfff473) until a restart.

## Intent
A global tool-call timeout enforced by agent's tool registry: every call gets a default (proposal: 60s); a tool's definition may declare its own default (exec keeps its `timeout` arg); config may override per crew and/or per tool. A timeout returns an ordinary tool error the model can react to, and the turn moves on. The abandoned work must not keep running unbounded (cancel the future/thread; for processes, kill).

## Open questions (plan before todo)
- Default value; config shape (`tools.timeout-ms` default + per-tool map? per-crew override?).
- Interaction with long legit tools (web_fetch, MCP tools, hail/comm sends) and with turn cancellation.
- Can a blocked JVM file read actually be interrupted? (A dataless iCloud read may not respond to interrupt; may need the call on its own thread and abandon it.)

## Decision + Acceptance (Micah, 2026-10-01; gated)

The tool registry runs every call against a deadline: `defaults.tools.timeout-ms` (default 60000), overridable by `crew.<id>.tools.timeout-ms`; a tool may declare its own `:timeout-ms` (number or fn of args; exec__run = its `timeout` arg + margin; web tools keep 30s). Past the deadline: ordinary tool error "timed out after <n>ms", log `:tool/timed-out` (warn, with :tool), turn continues; the work is interrupted, or abandoned and logged if it ignores the interrupt. Turn cancellation cancels in-flight calls the same way. Declare the config keys in the schema with descriptions.
- The @wip scenarios in isaac-agent `features/tool/timeout.feature` pass with @wip removed. New steps to add: "a fixture tool {name} that never returns is registered", "a fixture tool {name} that returns after {n}ms is registered".
- Unit specs: tool-declared default, cancellation of an in-flight call, abandoned call logged.
- Handbook chapter documents the timeout. `bb ci`, `bb jvm-spec`, `bb jvm-features` green.

feature-baseline: isaac-agent fead24a5574b7260b74a99b3453abfbe5168013b
feature-blob: isaac-agent features/tool/timeout.feature 8ee8a2879ccbf166256954c5b1290ac8113eecc0
