---
# isaac-qgtn
title: 'MCP stdio client: config, discover, register, execute'
status: in-progress
type: feature
priority: high
created_at: 2026-08-22T00:40:00Z
updated_at: 2026-08-22T16:44:59Z
parent: isaac-uhvt
---

First MCP slice. Does **not** wait on ek0r: invoke through the tool
registry, not a crew turn. Crew `:allow [:lens/*]` is isaac-6b5z.

Likely repo: **isaac-mcp**. Tiny cross-repo: **isaac-server**
`optional-registry-syms` (same requiring-resolve list as hail/hooks/cron)
unless `:mcp` schema `:factory` already reconciles a Reconfigurable
node without it. Prefer the schema-factory path if one line of
`isaac.config.berths` already does that.

## Decisions (2026-08-21, Micah)

- Config `:mcp` entity-dir, `:command` required; optional `:args` `:env` `:cwd`.
- Stdio JSON-RPC NDJSON. Prefix `serverid__toolname` (`lens__catalog`).
- Dead command: log `:mcp/connect-failed`, Isaac stays up, no tools.
- Execute via `isaac.tool.registry` (existing execute steps).
- One reused phrase: `the Isaac system is started` (cron/hail). Helper
  in `feature-steps/isaac/mcp_steps.clj` calls `isaac.mcp.runtime/start!`
  + `stop!` (after-scenario). **No new gherclj phrases.**

## Decisions (2026-08-22, plan overnight)

- Optional `:timeout-ms` int on the server schema (code default 30000).
  Hung calls return a normal tool error whose text contains `timeout`
  and log `:tool/execute-failed` (existing event — do not invent a
  second timeout event).
- Two config ids running the same fixture stay distinct
  (`lens__catalog` vs `skybeam__catalog`).
- Success connect logs `:info :mcp/connected` with `:server` = config id
  (`lens`).
- `start!` must **not** throw on a dead command.
- MCP `tools/list` names stay unprefixed on the wire to the server;
  Isaac registry/LLM name is `serverid__toolname`. Handler calls
  `tools/call` with the original MCP name.
- Fixture `test-resources/marigold/lens_mcp.bb`: stdio NDJSON MCP
  (`initialize`, `initialized`, `tools/list`, `tools/call`).
  `tools/list` returns **exactly** `catalog` and `read`. Both return
  text containing `marigold`. Query `stare` on `catalog` sleeps ≥10s
  (timeout scenario). Do not add a third listed tool — isaac-6b5z
  glob uses exact prompt-set equality.
- Do **not** use step `the tool "…" is called with:` — it
  `registry/clear!`. Use `tool "…" is executed with:`.
- `env` / `cwd` are passed through to the process (schema already has
  them). No feature for that in this bean.
- Hot reload of `:mcp` is out of scope; still implement
  `Reconfigurable` so a later bean is not a rewrite.

## Acceptance (`@wip` in isaac-mcp)

- `bb features features/config_validate.feature:10`
- `bb features features/config_validate.feature:29`
- `bb features features/lifecycle.feature:13`
- `bb features features/lifecycle.feature:26`
- `bb features features/lifecycle.feature:35`
- `bb features features/lifecycle.feature:46`
- `bb features features/lifecycle.feature:62`

New steps invented: none.

## Out of scope

- Crew allow / prompt tools / transcript (isaac-6b5z, needs ek0r).
- HTTP/SSE, OAuth, resources, prompts, ACP mcpServers, hot reload.
