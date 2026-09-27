---
# isaac-kxqj
title: Tool-call-scoped resource leases
status: draft
type: feature
priority: deferred
created_at: 2026-09-27T23:07:25Z
updated_at: 2026-09-27T23:09:08Z
parent: isaac-q3u3
blocked_by:
    - isaac-ey7a
---

Likely repo: **isaac-agent**. Split out of isaac-ey7a on 2026-09-27 (Micah + planner): no consumer exists yet.

## Idea

A lease acquired only when a specific tool runs and released when that call returns (e.g. a database connection held during one SQL tool call), instead of for the whole turn.

## Open questions (why this waits)

- What happens when the pool is busy mid-turn? Blocking inside the turn breaks the non-blocking acquisition rule in isaac-ey7a; failing the tool call may be the answer.
- How a tool declares the pool it needs, and how Agent releases on tool error or turn cancellation.

## Gate

Stays draft until a real consumer (a concrete database pool adapter) needs it. The isaac-ey7a receipt shape leaves room for a lease scope; only turn scope exists until this lands.
