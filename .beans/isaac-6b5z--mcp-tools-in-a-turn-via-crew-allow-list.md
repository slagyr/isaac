---
# isaac-6b5z
title: MCP tools in a turn via crew allow list
status: in-progress
type: feature
priority: high
created_at: 2026-08-22T00:40:00Z
updated_at: 2026-08-23T16:44:58Z
parent: isaac-uhvt
blocked_by:
    - isaac-ek0r
    - isaac-qgtn
---

After namespaced allowlists (ek0r) and MCP register (qgtn): a crew
`:allow [:lens/*]` (or `:lens/catalog`) sees `lens__catalog` /
`lens__read` on the prompt and a turn can invoke `lens__catalog`.
A dead server is not offered even when allowed.

Likely repo: **isaac-mcp** (`features/turn.feature`). Fixture is
qgtn's `test-resources/marigold/lens_mcp.bb`.

## Decisions (2026-08-21, Micah)

MCP tools are just tools on the existing crew `:tools :allow` list.
No `:tools :mcp` field. Config tokens are namespaced keywords
(`:lens/catalog`, `:lens/*`); the model sees `lens__catalog`.

## Decisions (2026-08-22, plan overnight)

- Scenarios use live ek0r tokens (`lens/*`, `lens/catalog`) and wire
  names (`lens__catalog`). Do not rewrite them back to bare `catalog`.
- `the prompt has tools:` is exact set equality. Fixture lists
  exactly `catalog` and `read`, so the glob scenario's table is
  `lens__catalog` + `lens__read` only.
- Unavailable = not registered, so not on the prompt. Do not assert
  a turn-level timeout here (qgtn already covers hung `tools/call`).
- New steps invented: none. Reuses grover, crew allow, sessions,
  queued responses, `the Isaac system is started`, prompt tools,
  transcript matching.

## Acceptance (`@wip` in isaac-mcp)

- `bb features features/turn.feature:12`
- `bb features features/turn.feature:31`
- `bb features features/turn.feature:51`
- `bb features features/turn.feature:65`

## Out of scope

- HTTP transport, ACP client-provided servers (isaac-zt4h).
- Hot reload of MCP servers.
- Changing allow-list matching (ek0r / da0r).
