---
# isaac-udph
title: "Drop on-error from the Comm protocol; impls branch in on-turn-end"
status: completed
type: task
priority: low
created_at: 2026-05-06T19:34:30Z
updated_at: 2026-05-06T19:52:08Z
---

## Description

Why: on-error is redundant. on-turn-end already receives the same  map (with :error set when applicable), so any comm that wants to render errors differently can branch:

  (on-turn-end [_ key-str result]
    (if-let [err (:error result)]
      (render-error err)
      (render-success result)))

Removing on-error simplifies the Comm protocol (11 methods -> 10), eliminates the on-error -> on-turn-end ordering invariant, and removes a no-op every comm impl has to remember to declare. Discord's on-error is currently nil; the existing call site at drive/turn.clj:545-549 only fires on-error for non-cancelled errors.

## Scope

- Remove on-error from defprotocol Comm in src/isaac/comm.clj.
- Update isaac.drive.turn/finish-turn! (drive/turn.clj:545-549): drop the (when ... on-error ...) clause; only call on-turn-end.
- Remove the on-error method from every Comm impl:
  - DiscordIntegration in modules/isaac.comm.discord/src/isaac/comm/discord.clj
  - Any CLI Comm impl
  - Any test/memory Comm impl
  - Any other impl in spec/ or features/
- Update specs that exercise on-error specifically (if any).

## Out of scope

- Bridge dispatch / silent run-turn! work (isaac-d4jz). on-error removal is independent — comm impls should already not rely on it for anything load-bearing.

## Acceptance

- isaac.comm/Comm has 10 methods (no on-error).
- finish-turn! calls on-turn-end only.
- No comm impl declares an on-error method.
- bb spec passes.
- bb features passes.
- Any comm-impl that wanted error-specific behavior now branches on (:error result) inside on-turn-end. (Confirmed not load-bearing for current impls.)

## Acceptance Criteria

on-error removed from Comm; finish-turn! no longer calls it; all comm impls drop the method; bb spec and bb features pass

## Notes

Verification failed: bb spec (1350 examples) and bb features (494 examples) both pass, and the main protocol/call-site changes are present: isaac.comm/Comm has no on-error method and finish-turn! only calls on-turn-end. However, acceptance is not fully met because a remaining Comm impl in specs still declares on-error: spec/isaac/bridge/chat_cli_spec.clj lines 856-870 reify comm/Comm and include (on-error [_ _ _] nil). The bead's acceptance says all comm impls drop the method, including spec/feature impls, so cleanup is incomplete.

