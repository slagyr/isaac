---
# isaac-4g2k
title: Global tool-call timeout in the agent tool registry
status: draft
type: feature
priority: normal
created_at: 2026-10-01T17:23:56Z
updated_at: 2026-10-01T17:23:56Z
---

Micah, 2026-10-01. Only `exec__run` has a timeout (30s default, per-call `timeout` arg). File tools (`fs__read`, `fs__glob`, `fs__grep`) and others have none: on zanebot a read of an iCloud-evicted (dataless) file and an unbounded home-directory glob each held a cron turn for ~7 hours (tempest-vault-sync 25f87b25, heartbeat 9bfff473) until a restart.

## Intent
A global tool-call timeout enforced by agent's tool registry: every call gets a default (proposal: 60s); a tool's definition may declare its own default (exec keeps its `timeout` arg); config may override per crew and/or per tool. A timeout returns an ordinary tool error the model can react to, and the turn moves on. The abandoned work must not keep running unbounded (cancel the future/thread; for processes, kill).

## Open questions (plan before todo)
- Default value; config shape (`tools.timeout-ms` default + per-tool map? per-crew override?).
- Interaction with long legit tools (web_fetch, MCP tools, hail/comm sends) and with turn cancellation.
- Can a blocked JVM file read actually be interrupted? (A dataless iCloud read may not respond to interrupt; may need the call on its own thread and abandon it.)
