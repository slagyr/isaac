---
# isaac-m14k
title: Agent re-loads config + re-registers all tools on every turn (should be boot/config-change only)
status: in-progress
type: bug
priority: high
created_at: 2026-06-20T23:46:43Z
updated_at: 2026-06-20T23:49:11Z
---

The agent re-runs the **entire config load** — module discovery, schema compose,
validation, entity-file reads, AND the full berth factory pass (re-registering
all tools) — on **every LLM turn**, per session. It should load/register once at
boot and re-run only when config changes (hot-reload).

Surfaced by olj5: `:berth/registered` fires not just at boot but on every turn,
flooding the structured log (13 agent tools × every turn × every session).

## Evidence (zanebot, foundation 0.1.6)
Each runtime re-registration sits inside the turn-startup sequence:
    :session/behavior-resolved
    :prompt/catalog-resolved
    :turn/context-resolved
       -> [13 :berth/registered for :isaac.agent/tools: read write edit grep glob
            exec memory_* session_* web_*]
    :drive/turn-accepted -> :turn/request-built -> :chat/stream-request
Two such passes (23:26:04, 23:27:15) = the two turns `main` took to handle one
hail. Plus 2 passes at boot (23:24:26) — boot itself loads config twice.

## Root cause
- `isaac.bridge.core` (turn dispatch, core.clj:78) falls back to
  `loader/snapshot` when the charge carries no `:config`, on every turn.
- `loader/snapshot` -> `isaac.config.loader/load-config-result` (loader.clj:810)
  is **uncached**: full `module-loader/discover!`, schema compose, validation,
  entity-file load, and `process-manifest-berths!` (the per-entry factories that
  register tools, loader.clj:678) — all re-run per call.
- So every turn re-discovers modules and re-invokes every berth factory.

## Desired behavior
- Config + berth registration happen ONCE at boot (module activation).
- Re-run ONLY on a config change (the hot-reload watcher; note zanebot has
  `:hot-reload false`, so today it would only need boot).
- Per-turn paths read the already-committed snapshot (nexus :config / the
  committed process-wide snapshot — infra exists: agent/config/install commits
  it) instead of calling `load-config-result` fresh.

## Acceptance
- A second+ LLM turn in a session emits NO `:berth/registered` (registration
  only at boot / on config change).
- Tool set per turn is still correct (read from committed snapshot).
- Boot itself registers each berth once, not twice.
- Measurable: turn-startup no longer re-runs `module-loader/discover!`.

## Notes
- Fixing the root removes the olj5 per-turn log noise — no need to suppress the
  event; it becomes a clean boot-only signal again.
- Relates to the boot double-pass (register-module-cli-commands! + server boot).
