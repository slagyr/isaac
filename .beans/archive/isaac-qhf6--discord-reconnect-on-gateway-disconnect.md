---
# isaac-qhf6
title: "Discord: reconnect on Gateway disconnect"
status: completed
type: feature
priority: low
created_at: 2026-04-20T23:07:43Z
updated_at: 2026-04-21T19:54:08Z
---

## Description

Milestone 7 of the Discord channel adapter epic. Handle Gateway disconnect robustly: respect Discord close codes, distinguish resumable vs non-resumable disconnects, exponential backoff on repeated failures, RESUME when possible (preserving session), fall back to re-IDENTIFY when not. Protocol reference: https://discord.com/developers/docs/topics/gateway#resuming. Depends on the reply bead.

## Acceptance Criteria

1. Implement close-code classification in isaac.comm.discord.gateway.
2. On resumable close: open a new WS and send RESUME op 6 with captured session_id and last sequence.
3. On re-identify close: open a new WS and send fresh IDENTIFY op 2.
4. On fatal close: log :discord.gateway/fatal-close at error level and do not reconnect.
5. Add the 2 step-defs listed above.
6. Remove @wip from both scenarios in features/comm/discord/reconnect.feature.
7. bb features features/comm/discord/reconnect.feature passes.
8. bb features passes overall.
9. bb spec passes.

## Design

Implementation notes:
- Discord close code classification (simplified):
  - Resumable (reconnect + RESUME op 6): 4000, 4001, 4002, 4003, 4008
  - Re-identify (reconnect + IDENTIFY op 2): 1000, 1001, 4007, 4009
  - Fatal (no reconnect, log error): 4004 (auth failed), 4010+ (privileged intents, invalid version)
- RESUME payload op 6: {:token, :session_id, :seq} — session_id and seq captured from the current gateway session state.
- Exponential backoff (repeated reconnect failures): 1s, 2s, 4s, 8s, ... capped at some max. Covered by unit spec; feature-level testing requires virtual time coordination.

New step-defs:
- 'Discord closes the connection with code {n:int}' — fires the on-close callback of the fake WS transport with the given status code. The client reads the code and dispatches to its reconnect logic.
- 'the Discord client sends RESUME:' — asserts op 6 was sent to the reconnected WS (k-v table of payload fields, same shape as sends IDENTIFY: from gateway.feature).

