---
# isaac-833l
title: "Split isaac.bridge into focused namespaces (cancellation, status, core)"
status: completed
type: task
priority: low
created_at: 2026-05-08T15:52:48Z
updated_at: 2026-05-08T16:47:31Z
---

## Description

isaac.bridge is 378 lines of three distinct concerns mixed together. The file's own region markers tell the story:

- ;; region Cancellation        (lines 14-75)   — ~60 lines
- ;; region Status Command       (lines 77-245)  — ~170 lines
- ;; region Turn Resolution      (lines 247-303) — ~55 lines
- ;; region Triage               (lines 305-378) — ~75 lines

Eight external callers each use a specific subset, but today they all require `[isaac.bridge :as bridge]` and pluck out their handful — readers can't tell at a glance which concern any caller depends on.

## The split

Move each cluster to its own ns under isaac.bridge.*. Drop the parent file (isaac/bridge.clj deletes; no façade — the codebase has been preferring concrete wiring over re-export indirection).

### isaac.bridge.cancellation (~60 lines)

Public surface:
- begin-turn!, end-turn!
- cancel!, cancelled?, on-cancel!
- cancelled-result, cancelled-response?

State: active-turns, pending-cancels atoms (private).

### isaac.bridge.status (~170 lines)

Public surface:
- status-data
- format-status
- available-commands

### isaac.bridge.core (~130 lines)

Holds Turn Resolution + Triage regions:
- resolve-turn-opts
- slash-command?
- dispatch
- dispatch!

This is the orchestration layer that ties cancellation + status + drive together. "core" because dispatch IS the bridge's primary job; the others are supporting concerns.

## Caller migration (verified by grep)

**Cancellation cluster — 5 callers:**
- src/isaac/llm/http.clj             — cancelled?, on-cancel!
- src/isaac/llm/api/grover.clj       — cancelled?
- src/isaac/drive/turn.clj           — begin-turn!, end-turn!, cancelled?, cancelled-response?, cancelled-result, on-cancel!
- src/isaac/tool/builtin.clj         — cancelled?
- src/isaac/comm/acp/server.clj      — cancel!, cancelled-response? (also uses dispatch)

**Status cluster — 1 caller:**
- src/isaac/comm/acp/server.clj      — available-commands

**Core (dispatch) cluster — 5 callers:**
- src/isaac/api.clj                  — dispatch!
- src/isaac/comm/acp/server.clj      — dispatch!
- src/isaac/server/hooks.clj         — dispatch!
- src/isaac/cron/scheduler.clj       — dispatch!
- src/isaac/bridge/prompt_cli.clj    — dispatch! (sub-namespace caller)

Note: comm/acp/server uses all three clusters (cancel!, available-commands, dispatch!).

Specs migrate alongside (mostly mechanical alias updates in spec/isaac/bridge_spec.clj, spec/isaac/comm/acp/server_spec.clj, etc.).

## Why no transitional pass-through

Unlike isaac-o3da (which spans many callers and has heavy internal coupling), this is 8 callers + bridge-internal sub-files, all mechanical alias swaps. Land it as one coordinated commit. No middle-state needed.

## Out of scope

- Behavior changes (cancellation semantics, status format, dispatch logic all unchanged).
- Touching isaac.bridge.chat-cli or isaac.bridge.prompt-cli internals (they remain CLI entry-point sub-namespaces; they just update their require to point at the new homes).
- Moving the bridge under another umbrella (isaac.comm.bridge, isaac.drive.bridge, etc. — bridge mediates between comm and drive, not owned by either).

## Acceptance Criteria

bb spec green; bb features green; src/isaac/bridge.clj deleted; isaac.bridge.cancellation, isaac.bridge.status, isaac.bridge.core all exist with their respective public surfaces; the 8 external callers + isaac.bridge.prompt-cli use specific sub-ns requires; no caller still requires isaac.bridge; behavior unchanged from caller perspective.

