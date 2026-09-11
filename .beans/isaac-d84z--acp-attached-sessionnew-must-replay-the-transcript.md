---
# isaac-d84z
title: 'ACP: attached session/new must replay the transcript like session/load'
status: completed
type: bug
priority: normal
created_at: 2026-07-07T19:01:30Z
updated_at: 2026-07-07T19:50:39Z
parent: isaac-zt4h
---


## Gap

`isaac acp --session <existing>` overrides session/new to return the bound session's id (cli.clj attach-session-handler) — the client is genuinely attached to the existing session and the model sees full history — but the attach path returns the sessionId WITHOUT replaying the transcript. Replay lives only in session/load, which a client that just got a successful session/new never calls. Observed live (Micah, 2026-07-07, Toad + --session misty-cypress): the model remembers everything, the client window shows nothing.

## Fix

When session/new is attached to an existing session, stream the transcript replay (the same session/update notifications session/load emits — user/agent chunks, tool_call replays, compaction summaries) before/with the session/new response. From the client's view, attaching to history and loading history must be indistinguishable. Reuse attach-session-result!/replay-transcript! — the machinery exists; the attach handler just doesn't call it.

## Acceptance sketch (spec to confirm)

isaac-acp feature: Given a session with transcript entries and an acp server bound with --session, When a client sends initialize + session/new, Then the response carries the bound sessionId AND session/update replay notifications for each transcript entry precede/accompany it — asserted with the same frame-matching machinery as the existing session/load scenarios.

## Implementation Notes

- Implemented in sibling repo `isaac-acp`.
- `src/isaac/comm/acp/cli.clj` now routes attached `session/new` through `isaac.comm.acp.server/attach-session-result!` instead of returning only `{:sessionId ...}`. This reuses the existing transcript replay path used by `session/load`.
- Added a focused CLI unit spec proving attached `session/new` emits replay notifications before the final response.
- Updated the CLI feature scenario to assert both transcript replay notifications and the final `sessionId` response for `acp --session <existing>`.
- Implementation commit: `ebcd241` (`Replay transcript on attached ACP session/new`).
- Verification run:
  - `bb spec spec/isaac/comm/acp/cli_spec.clj`
  - `bb features features/comm/acp/cli.feature`
  - `bb spec`
  - `bb features`
- Full repo suites passed with existing pending scenarios only:
  - specs: `67 examples, 0 failures, 189 assertions, 1 pending`
  - features: `59 examples, 0 failures, 131 assertions, 5 pending`
