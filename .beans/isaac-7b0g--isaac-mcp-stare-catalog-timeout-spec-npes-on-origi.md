---
# isaac-7b0g
title: 'isaac-mcp: stare catalog timeout spec NPEs on origin/main'
status: draft
type: bug
priority: high
tags:
    - ci
created_at: 2026-09-19T03:48:48Z
updated_at: 2026-09-19T03:48:48Z
---

Split from isaac-okw1. Pre-existing on isaac-mcp origin/main independently of the agent/foundation pin.

`isaac.mcp.client` spec "returns a timeout error when catalog query is stare" NPEs. Reproduced on origin/main with pin edits stashed (lsz2 @ 44c408d also recorded 32/1). isaac-okw1 must not absorb this.

## Observed

isaac-mcp `bb spec` 32 examples, 1 failure. Catalog query `stare` is supposed to sleep and return a timeout error; the spec NPEs instead (likely `isaac.mcp.client` timeout path).

## Acceptance (draft — scenarios at promotion)

Repair the timeout path / spec so `bb spec` is 0 failures on origin/main. Do not recut pins. Do not absorb discord.

```
cd isaac-mcp && bb spec
```
