---
# isaac-d4jz
title: "Bridge dispatch + silent run-turn! (fix slash-command output for non-CLI comms)"
status: completed
type: task
priority: normal
created_at: 2026-05-06T19:16:53Z
updated_at: 2026-05-06T20:30:05Z
---

## Description

Why: today, isaac.session.bridge handles slash-commands but is dispatched from INSIDE run-turn!, with output via println to stdout. Comms have no chance to intercept before the turn starts, and slash-command output goes through stdout. For Discord, the gateway calls process-message! which wraps the call in with-out-str — capturing AND discarding the slash-command println. **Live bug**: Discord users typing /status, /crew, /model, /cwd get no response.

Three intertwined fixes that need to ship together.

## Scope

### 1. Move and rename isaac.session.bridge -> isaac.bridge

The bridge layer is broader than session — it dispatches every inbound message. Move src/isaac/session/bridge.clj to src/isaac/bridge.clj. Update :requires across the project.

### 2. Comms dispatch via bridge, not directly via run-turn!

- Add isaac.bridge/dispatch! (or similar) as the comm-facing entry point.
- dispatch! triages: slash-command (handle in bridge), normal turn (delegate to drive/run-turn!), future non-turn flows.
- Discord's process-message! calls bridge/dispatch! instead of api/run-turn!.
- CLI's chat-cli / prompt-cli flows route through bridge/dispatch! the same way.
- ACP and other comms follow the same pattern.

Invariant: bridge -> drive direction only. drive (run-turn!) does NOT call bridge for slash-command detection.

### 3. Make run-turn! silent

- Remove (println output) in handle-bridge-command (drive/turn.clj:551).
  - Move slash-command output emission into bridge itself, via Comm callbacks (on-text-chunk and on-turn-end on the comm passed into dispatch!).
- Remove print-streaming-response and any other stdout writes from run-turn!'s call chain.
  - CLI's stdout writing belongs in the CLI Comm impl (isaac.bridge.chat-cli / prompt-cli), which receives chunks via on-text-chunk and writes to stdout there.
- run-turn! emits exclusively via Comm callbacks. No stdout side effects.

### 4. Drop with-out-str from Discord process-message!

Once run-turn!/bridge are silent, Discord's defensive (with-out-str ...) wrapper is unnecessary. Replace with a plain (api... call ...). Same cleanup applies to any other comm that might be silencing stdout defensively.

## Acceptance

- src/isaac/bridge.clj exists; src/isaac/session/bridge.clj is gone.
- Comms call isaac.bridge/dispatch! (or equivalent named entry); none call run-turn! directly except via the bridge.
- run-turn! has no println, no print, no print-streaming-response calls. Only Comm-callback emission.
- handle-bridge-command (or its replacement) emits slash-command output via the comm, not stdout.
- Discord's process-message! has no with-out-str.
- CLI continues to print to stdout, but via its own Comm impl receiving on-text-chunk events.
- features/comm/discord/* still pass; features/cli/* still pass; the live Discord slash-command bug is fixed (a /status in Discord produces a reply).

## Worth flagging during implementation

- Bridge's slash-command handlers (handle-model, handle-crew, handle-cwd, format-status) currently return strings. They'll need to call comm/on-text-chunk + comm/on-turn-end (or equivalent) to deliver via the comm.
- run-turn! is consumed by 5 callsites (Discord, hooks, cron, CLI prompt, ACP). After silencing, each caller's Comm impl must implement on-text-chunk for streaming behavior to be preserved.
- This is bigger than the typical bead — touches drive/turn.clj substantially. Worker should expect a sizable diff.

## Acceptance Criteria

isaac.bridge/dispatch! is the comm-facing entry; comms route slash-commands and turns through it; run-turn! is silent (no stdout writes); with-out-str gone from Discord; live slash-command bug fixed across all comms; existing features pass

## Notes

Verification failed on re-review: bb spec (1354 examples) and bb features (494 examples) pass, and Discord/prompt CLI are routing through bridge/dispatch!, but ACP still bypasses the comm-facing bridge entrypoint. In src/isaac/acp/server.clj, run-prompt still branches on bridge/slash-command? and calls bridge/dispatch for slash commands (lines 246-259), while normal ACP turns still call single-turn/run-turn! directly in run-acp-turn (lines 220-244). The bead acceptance says comms route slash-commands and turns through isaac.bridge/dispatch! and that ACP follows the same pattern, so this remains incomplete.

