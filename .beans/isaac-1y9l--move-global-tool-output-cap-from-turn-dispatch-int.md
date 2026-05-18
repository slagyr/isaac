---
# isaac-1y9l
title: Move global tool-output cap from turn dispatch into tool.registry/execute
status: todo
type: feature
priority: normal
created_at: 2026-05-18T15:05:10Z
updated_at: 2026-05-18T15:15:35Z
---

## Problem

`isaac-q7x6` shipped the global tool-output cap by applying it at the turn-dispatch layer (`src/isaac/drive/turn.clj:139-158`). That's the wrong abstraction — the cap is a property of "tool output," not of "turn dispatch." Consequences today:

- Direct invocations through `tool.registry/execute` (`src/isaac/tool/registry.clj:120-152`) bypass the cap entirely. Tools without internal truncation (`exec`, `memory_get`, `memory_search`, `session_info`, `session_model`) can return unbounded output.
- The feature-test harness has to manually wrap `registry/execute` with the cap to make tests realistic (`spec/isaac/features/steps/tools.clj:159-166`, `:347-358`). That wrapper is paper-over — production tests don't actually exercise the production registry path.
- Any future caller that invokes tools without going through turn dispatch (slash commands invoking tools, hooks running scripted tools, MCP relays, scripted CLI invocations) is unbounded.

## Approach

Move `output-cap/cap-result` from `drive/turn.clj` into `tool.registry/execute`. Every tool invocation, regardless of caller, is bounded by `tools.defaults.max-lines` and `tools.defaults.max-bytes`. Per-tool self-truncation (`read`'s `limit` arg, `web_fetch`'s line cap, `grep`'s `head_limit`, `glob`'s `head_limit`) stays in place as defense in depth and user-controlled trimming.

The cap-result helper itself doesn't change — it's `src/isaac/tool/output_cap.clj:10-35` already. Only its call site moves.

## Scope

- `tool.registry/execute` applies `cap-result` before returning
- `drive/turn.clj:139-158` drops its cap invocation (the registry already capped)
- `spec/isaac/features/steps/tools.clj:159-166, 347-358` drops the manual wrapper
- Feature scenarios in `features/tools/output_cap.feature` continue to pass — the cap is now actually in the production path being exercised

## Out of scope

- Per-tool self-truncation (`read`, `web_fetch`, `grep`, `glob`) — stays as user-controlled finer-grained trimming
- New cap configuration / tuning — uses the same `tools.defaults.*` keys
- Tools that intentionally produce structured large output (e.g., a future `dump` tool) needing exemption — punt to a per-tool opt-out flag if/when needed

## Relationship

- Follows up on `isaac-q7x6`. q7x6 stays "completed" — it shipped what it specified. This bean fixes the layering.
- Independent of `isaac-bv48` (session-behavior funnel) — different subsystem.

## Acceptance

```
bb features features/tools/output_cap.feature
```

All scenarios pass with the cap moved to the registry layer. The feature-test harness wrapper at `spec/isaac/features/steps/tools.clj:159-166` and `:347-358` is removed without breaking any spec.

## Feature file

Existing `features/tools/output_cap.feature` covers this — no new scenarios needed; the existing scenarios become real production-path tests rather than tests-with-harness-help. May want to add one scenario that exercises `tool.registry/execute` *directly* (no turn dispatch) to lock in the registry-layer cap as the contract.
