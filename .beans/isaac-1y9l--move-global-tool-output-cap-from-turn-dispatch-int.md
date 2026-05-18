---
# isaac-1y9l
title: Move global tool-output cap from turn dispatch into tool.registry/execute
status: todo
type: feature
priority: normal
created_at: 2026-05-18T15:05:10Z
updated_at: 2026-05-18T15:23:15Z
---

## Problem

`isaac-q7x6` shipped the global tool-output cap by applying it at the turn-dispatch layer (`src/isaac/drive/turn.clj:139-158`). That's the wrong abstraction — the cap is a property of "tool output," not of "turn dispatch." Consequences today:

- Direct invocations through `tool.registry/execute` (`src/isaac/tool/registry.clj:120-152`) bypass the cap entirely. Tools without internal truncation (`exec`, `memory_get`, `memory_search`, `session_info`, `session_model`) can return unbounded output.
- The feature-test harness has to manually wrap `registry/execute` with the cap to make tests realistic (`spec/isaac/features/steps/tools.clj:159-166`, `:347-358`). That wrapper is paper-over — production tests don't actually exercise the production registry path.
- Any future caller that invokes tools without going through turn dispatch (slash commands invoking tools, hooks running scripted tools, MCP relays, scripted CLI invocations) is unbounded.

## Approach

Two changes that together unify on a single source of truth for tool-output bounds:

1. **Move `output-cap/cap-result` from `drive/turn.clj` into `tool.registry/execute`.** Every tool invocation, regardless of caller, is bounded by `tools.defaults.max-lines` and `tools.defaults.max-bytes`.
2. **Remove hardcoded per-tool default limits.** Four tools currently carry their own internal defaults that fire when the user/model doesn't pass an arg:
   - `src/isaac/tool/file.clj:9` — `*default-read-limit* 2000`
   - `src/isaac/tool/web_fetch.clj:9` — `*default-limit* 2000`
   - `src/isaac/tool/grep.clj:9` — `*default-head-limit* 250`
   - `src/isaac/tool/glob.clj:12` — `*default-head-limit* 100`

   These are redundant once the registry cap is reliable, and they create a second source of truth ("why does read truncate at 2000 lines but exec at 256KB?"). Drop them. When the call doesn't pass a `limit` / `head_limit` arg, the tool returns everything and the registry caps it.

What stays:

- **User-controlled args** (`read`'s `limit`, `grep`'s `head_limit`, `glob`'s `head_limit`) — these are finer-grained user controls ("first 100 lines"), not output bounds. Not the same thing as defaults.
- **The cap-result helper itself** (`src/isaac/tool/output_cap.clj:10-35`) — unchanged. Only its call site moves.

## Behavior change

A default `read` of a 10MB minified-JSON file today returns ~2000 short lines (~tens of KB). After this bean, it returns whatever fits in 256KB — could be one giant line. Models that rely on line-count semantics for unspecified `read` should start passing an explicit `limit` arg. Same shape of change for the other three tools.

## Scope

- `tool.registry/execute` applies `cap-result` before returning
- `drive/turn.clj:139-158` drops its cap invocation (the registry already capped)
- `spec/isaac/features/steps/tools.clj:159-166, 347-358` drops the manual wrapper
- The four hardcoded per-tool defaults are removed:
  - `src/isaac/tool/file.clj:9` — `*default-read-limit*`
  - `src/isaac/tool/web_fetch.clj:9` — `*default-limit*`
  - `src/isaac/tool/grep.clj:9` — `*default-head-limit*`
  - `src/isaac/tool/glob.clj:12` — `*default-head-limit*`
  
  Each tool's implementation falls through to "no truncation" when the user-arg isn't supplied; the registry cap handles it.
- Existing specs that assumed the old per-tool line caps (e.g., tests on `read` of a 20-line file) get recalibrated against the global cap or against explicit `limit` args
- Feature scenarios in `features/tools/output_cap.feature` continue to pass — the cap is now actually in the production path being exercised

## Out of scope

- User-controlled `limit` / `head_limit` args — stay; they are user controls, not output bounds, and removing them would break valid usage like "give me lines 50-100 of foo.clj"
- New cap configuration / tuning — uses the same `tools.defaults.*` keys
- Tools that intentionally produce structured large output (e.g., a future `dump` tool) needing exemption — punt to a per-tool opt-out flag if/when needed

## Relationship

- Follows up on `isaac-q7x6`. q7x6 stays "completed" — it shipped what it specified. This bean fixes the layering.
- Independent of `isaac-bv48` (session-behavior funnel) — different subsystem.

## Acceptance

```
bb features features/tools/output_cap.feature
bb spec
```

- All scenarios pass with the cap moved to the registry layer
- The feature-test harness wrapper at `spec/isaac/features/steps/tools.clj:159-166` and `:347-358` is removed without breaking any spec
- A grep across `src/isaac/tool/` for `\*default-(read-|head-)?limit\*` returns no results
- `tools.defaults.max-lines` / `max-bytes` is the **only** configuration knob for tool-output bounds in the codebase

## Feature file

Existing `features/tools/output_cap.feature` covers this — no new scenarios needed; the existing scenarios become real production-path tests rather than tests-with-harness-help. May want to add one scenario that exercises `tool.registry/execute` *directly* (no turn dispatch) to lock in the registry-layer cap as the contract.
