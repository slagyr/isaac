---
# isaac-6b5z
title: MCP tools in a turn via crew allow list
status: completed
type: feature
priority: high
created_at: 2026-08-22T00:40:00Z
updated_at: 2026-08-23T21:58:41Z
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



## Held (awaiting human, 2026-08-23)

Escalated to human by **scrapper**@isaac-work-1. Blocking: slagyr-assistant has READ-only on slagyr/isaac-mcp, so the landed product commit cannot be pushed to origin/main.

Work is complete locally and green. Not handed to verify because the verifier would review origin, not this dirty-ahead checkout.

### Landed (local only, not on origin)

isaac-mcp **c1dbf20** on main (ahead of origin/fd3a0da):
- Dropped `@wip` on `features/turn.feature` (scenarios unchanged).
- Pin isaac-agent `10093b4` → **2522f77** (ek0r namespaced allow).
- Pin isaac-foundation `a834445` → **16f4786** (`fs/size` required by that agent).
- No product allow-list change. MCP tools stay registry tools; crew `:allow [:lens/*]` / `:lens/catalog` filters them like any other wire.

### Verified this turn

- `bb features features/turn.feature` **4/0** (all four acceptance selectors)
- Isolated `:12 :31 :51 :65` each GREEN
- `bb features features/config_validate.feature features/lifecycle.feature` **7/0** (qgtn suites; first combined run had a flake on hung-call timeout, isolated + rerun GREEN)
- `bb spec` **16/0** (native bb; earlier NPE on stare/timeout was flake, rerun GREEN)
- `bb jvm-spec spec/isaac/mcp/client_spec.clj` **5/0**

### Push failure

```
ERROR: Permission to slagyr/isaac-mcp.git denied to slagyr-assistant.
viewerPermission: READ  (agent + isaac repos are write)
```

`modules.edn` still pins isaac.mcp at scaffold **f1f441e**. Update to c1dbf20 only after origin has that SHA.

Resumes only on explicit human action (grant push on isaac-mcp, push c1dbf20, then re-hail the work/plan band). No crew re-picks this until then.


## HOLD resolved (2026-08-23)

`slagyr-assistant` can push `isaac-mcp`. Pushed **c1dbf20** (`fd3a0da..c1dbf20`) from work-1. `modules.edn` pin updated. Handing to verify.
