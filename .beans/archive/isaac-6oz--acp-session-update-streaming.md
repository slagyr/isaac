---
# isaac-6oz
title: "ACP: session/update streaming"
status: completed
type: task
priority: normal
created_at: 2026-04-10T21:10:24Z
updated_at: 2026-04-10T21:51:33Z
---

## Description

Emit session/update notifications with text chunks as the LLM generates them, so ACP front-ends can render text incrementally.

## Scope
- Hook into the streaming path of Isaac's chat flow
- For each chunk produced by the provider, emit a session/update notification with sessionUpdate: 'agent_message_chunk' and the chunk text
- The final stored assistant message remains the full concatenated text
- Required step change (from infra bead): responses-queued step parses EDN vector for chunked content

Parent epic: isaac-new
Feature file: features/acp/streaming.feature (1 @wip scenario)

## Acceptance
Remove @wip and verify:
- bb features features/acp/streaming.feature:19

Full suite: bb features and bb spec pass.

