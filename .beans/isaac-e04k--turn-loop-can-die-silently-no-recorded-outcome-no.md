---
# isaac-e04k
title: Turn loop can die silently — no recorded outcome, no attention
status: todo
type: task
created_at: 2026-08-21T14:54:09Z
updated_at: 2026-08-21T14:54:09Z
---

Observed 2026-08-21 on isaac-work-1 (scrapper) during isaac-qxvl attempt 2: after a benign tool/execute-failed (read of nonexistent file) at 14:43:25Z, the turn issued NO further LLM calls — no final assistant message, no canned loop-exhaustion text, no error in logs, no hold/handoff, session left in-flight-looking with 8h of uncommitted workdir changes. Server process healthy (Discord heartbeats continued). A turn must never end without a recorded outcome: expected = final transcript entry + loud log + attention notification on abnormal death. Repro context: very long turn (~8h, ~1939 compactions in session history), grok-4.6, tool loop. Candidate suspects: swallowed exception between tool-result append and next dispatch; tool-loop/compaction interaction at extreme turn length.
