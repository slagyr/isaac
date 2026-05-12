---
# isaac-aept
title: "Compaction status should emit as a thought, not a regular message"
status: completed
type: bug
priority: normal
created_at: 2026-04-28T17:44:54Z
updated_at: 2026-04-28T17:58:15Z
---

## Description

src/isaac/drive/turn.clj:327-328 emits compaction status as a regular
text chunk:

  (when channel
    (comm/on-text-chunk channel key-str "compacting..."))

This surfaces "compacting..." as if the assistant said it. It is
operational metadata, not the assistant's reply.

## Spec

features/acp/compaction_notification.feature now asserts:

  | session/update | agent_thought_chunk | compacting... |

(was agent_message_chunk; updated in place per the project's "change
the spec, implementation makes it pass" convention)

## Implementation

1. Add a method to the Comm protocol (src/isaac/comm.clj):

     (on-thought-chunk [ch session-key text])

2. Implementations:
   - src/isaac/comm/cli.clj      print to stderr or skip
   - src/isaac/comm/acp.clj      emit ACP agent_thought_chunk
                                 (mirror text-update notification
                                 shape but with sessionUpdate
                                 = "agent_thought_chunk")
   - src/isaac/comm/discord.clj  no-op
   - src/isaac/comm/memory.clj   record as a thought event
   - src/isaac/comm/null.clj     no-op

3. src/isaac/drive/turn.clj:328 — replace on-text-chunk with
   on-thought-chunk for the "compacting..." emit.

## Definition of done

- features/acp/compaction_notification.feature passes the updated
  scenario asserting agent_thought_chunk.
- comm/Comm has on-thought-chunk and all implementations updated.
- bb spec + bb features green.

## Notes

Added on-thought-chunk to comm/Comm, updated ACP/CLI/Discord/Memory/Null implementations, and switched compaction status in src/isaac/drive/turn.clj from on-text-chunk to on-thought-chunk. Verification: bb spec passes. features/acp/compaction_notification.feature passes. Full bb features has one unrelated failure in ACP Proxy Reconnect (result.stopReason nil instead of end_turn), tracked in the new follow-up bead created from this session.

