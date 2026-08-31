---
# isaac-i5ps
title: 'Scuttlebutt: ACP renders chatter/reckoning/asides'
status: draft
type: feature
priority: normal
created_at: 2026-08-30T22:48:18Z
updated_at: 2026-08-31T15:46:39Z
blocked_by:
    - isaac-5nxf
---

Repo: isaac-acp (after its pin bump in isaac-6yg0). Migrate to the new Comm via extend+defaults. Rendering: on-chatter → agent_message_chunk (as today's on-text-chunk); on-reckoning → agent_thought_chunk (toad shows it like Claude Code's thinking); tool events unchanged; bulletins → session/update status notifications (compaction notification scenario migrates). Draft until scenarios are written.



## Contract (decided in isaac-frvu ACP review, 2026-08-31 — option B)

Thought channel = stderr, message channel = stdout:
- on-chatter → agent_thought_chunk, live, '💬 ' prefix.
- on-reckoning → agent_thought_chunk, '🧭 ' prefix (feed the existing unused
  thought-notification builder).
- on-reply → agent_message_chunk, whole reply at the verdict — the only
  message content. Reply duplication with the thought stream is accepted.
- on-aside: default (no rendering).
- tool-call/result unchanged; on-tool-progress → tool_call_update
  status in_progress with appended content.
- compaction bulletins → thought chunks with 🥬✨🥀🪦 (replacing the
  words-only notices); other bulletin kinds default.
- Existing prompt/streaming/tool scenarios migrate: text that asserted
  agent_message_chunk mid-turn becomes thought-chunk assertions; the
  message assertion moves to the verdict. Those are behavior changes —
  @wip each migrated scenario.

Still blocked by isaac-5nxf (needs the new protocol + signals; also bump
pins past it — the 6yg0 pin-bump prerequisite is done, 6yg0 completed).
Promote to todo once scenarios are committed @wip.
