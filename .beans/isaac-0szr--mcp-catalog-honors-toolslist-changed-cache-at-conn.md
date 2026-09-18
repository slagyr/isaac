---
# isaac-0szr
title: 'MCP catalog honors tools/list_changed: cache at connect, re-catalog on notification'
status: in-progress
type: feature
priority: normal
tags:
    - mcp
created_at: 2026-09-18T02:43:52Z
updated_at: 2026-09-18T03:10:01Z
parent: isaac-uhvt
blocked_by:
    - isaac-vadd
---

Child of isaac-uhvt, follows isaac-vadd. Keep the once-per-connect catalog, but honor the MCP protocol's way of saying a catalog moved.

## Decision (2026-09-18, Micah)

Do not re-catalog every turn. Cache at connect. A server that declares `capabilities.tools.listChanged` may send `notifications/tools/list_changed`; when Isaac sees it, the server is re-catalogued before tools are offered on the next turn. A server without the flag is never asked again.

## Today (isaac-mcp `b2ee765`)

- `client/connect!` sends `initialize` and discards the server's capabilities.
- `client/request!` skips every line that is not the awaited response — a `list_changed` notification is dropped.
- `runtime` catalogs once in `connect-server!`; only a dead process triggers a re-list.

## Work (isaac-mcp only)

| piece | change |
|---|---|
| `client/connect!` | keep the initialize result's `capabilities` on the client map; `(list-changed? client)` reads `tools.listChanged` |
| `client/request!` | while skipping non-matching lines, a `notifications/tools/list_changed` message flags the client dirty (`:dirty*` atom on the client). No background reader — a notification sent while idle is seen at the next request, which is acceptable: it is re-catalogued for the turn after |
| `runtime` | `ensure-server!`: when the live client is dirty, `tools/list` again, unregister names that vanished, register new/changed ones, clear dirty, log `:mcp/recatalogued :server id`. `ensure-policy-tools!` already runs before every turn's cascade, so no agent change |
| fixture | `test-resources/marigold/lens_mcp.bb`: `--list-changed` arg advertises the capability; a `grow` tool appends `extra` to its catalog and emits the notification after replying |

## Scenarios (write `@wip` into `features/catalog.feature` on isaac-mcp main once isaac-vadd has merged; existing steps only)

1. **a server that announces list_changed is re-catalogued on the next turn** — config lens with `--list-changed`, crew allows `lens/*`, queued: turn 1 `tool_call lens__grow`, text; turn 2 text. User sends twice on one session. Then the prompt has tools `lens__catalog lens__read lens__grow lens__extra`; log has `:info :mcp/recatalogued server lens`.
2. **a server without listChanged keeps its catalog** — same without the flag. Second prompt does not have `lens__extra`.

## Acceptance

```
cd isaac-mcp && bb features features/catalog.feature
cd isaac-mcp && bb ci
```

Unit specs for: capabilities kept; dirty set by the notification during a read; re-catalog registers/unregisters the diff; no re-list without the flag.

## Estimate

Half a worker day: ~2–3 h implementation (client + runtime + fixture + specs), the rest on the two scenarios and verify. No cross-repo pins.

## Out of scope

Background reader thread (would see idle notifications immediately; revisit only if a real server needs it). Hot reload of `:mcp` config.
