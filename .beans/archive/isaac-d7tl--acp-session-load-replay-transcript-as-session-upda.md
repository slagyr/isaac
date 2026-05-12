---
# isaac-d7tl
title: "ACP session/load: replay transcript as session/update notifications"
status: completed
type: feature
priority: normal
created_at: 2026-04-29T21:02:14Z
updated_at: 2026-05-05T22:44:51Z
---

## Description

ACP session/load currently returns sessionId without sending any
notifications. Per the ACP spec, the agent MUST replay the entire
conversation to the client as session/update notifications, then
respond to the original session/load request after all history has
been streamed.

Spec source: https://agentclientprotocol.com/protocol/session-setup

## Spec

features/acp/session.feature has three new @wip scenarios:

1. session/load replays the transcript as session/update notifications
   - user messages -> user_message_chunk
   - assistant messages -> agent_message_chunk
   - in JSONL order

2. session/load replays the compaction summary in place of pre-
   compaction history
   - compaction entries render as agent_message_chunk with the
     summary text
   - bounded volume for free: pre-compaction messages are no
     longer in the transcript so they can't be replayed

3. session/load replays tool calls with their results
   - one tool_call notification per historic tool, status: completed
     (not the live pending -> completed two-step)
   - toolCallId preserved so client can render

## Implementation surfaces

src/isaac/acp/server.clj session-load-handler
  - Walk storage/get-transcript in order
  - For each entry, emit the appropriate notification via
    isaac.comm.acp/text-update / a new tool-call replay notification
    builder / etc.
  - After all replays, return the response (currently returns
    {:sessionId ...}; spec says result: null after replay)

src/isaac/comm/acp.clj
  - Add user-message-notification builder (we currently have
    text-notification for agent_message_chunk only)
  - Possibly a replay-tool-call-notification that emits a single
    tool_call with status: completed and result inline

## Spec drift to fix

The existing 'session/load resumes a prior session' scenario
asserts result.sessionId on the response. Per spec the response
should be result: null after history streaming. Update that
assertion as part of this work or in a separate cleanup.

## Definition of done

- All three @wip scenarios pass
- Existing session/load scenario updated to match spec response
  shape (or kept with a note explaining intentional drift)
- Manual: open Toad against a session that has transcript +
  compaction + tool calls; observe history rendered
- bb features and bb spec green

## Out of scope

- Pagination / bounded replay (spec doesn't define it; compactions
  bound the volume in practice)
- Replay-end client-side marker (spec uses the response itself
  as the marker)

## Notes

Verification failed: automated checks are green (session.feature scenarios pass with no @wip), but the bead definition of done still requires a manual Toad replay check against a session containing transcript + compaction + tool calls. That UI replay evidence is not present here.

