---
# isaac-e04k
title: Turn loop can die silently — no recorded outcome, no attention
status: completed
type: task
priority: normal
created_at: 2026-08-21T14:54:09Z
updated_at: 2026-08-21T17:58:52Z
---

Observed 2026-08-21 on isaac-work-1 (scrapper) during isaac-qxvl attempt 2: after a benign tool/execute-failed (read of nonexistent file) at 14:43:25Z, the turn issued NO further LLM calls — no final assistant message, no canned loop-exhaustion text, no error in logs, no hold/handoff, session left in-flight-looking with 8h of uncommitted workdir changes. Server process healthy (Discord heartbeats continued). A turn must never end without a recorded outcome: expected = final transcript entry + loud log + attention notification on abnormal death. Repro context: very long turn (~8h, ~1939 compactions in session history), grok-4.6, tool loop. Candidate suspects: swallowed exception between tool-result append and next dispatch; tool-loop/compaction interaction at extreme turn length.


## Implementation (scrapper@isaac-work-1)

Product commit **67d7838** on isaac-agent main.

- `run-turn!` catches ExceptionInfo (non-cancel), Exception, and Throwable; records a closing error transcript entry via `record-exception!`, logs `:error :session/turn-failed`, posts turn-failed attention, and `finish-turn!` — does not rethrow. `finally` still `end-turn!`.
- Registered step `the LLM throws an exception with message {message}` (grover `:type exception`). Dropped `@wip` on `uncaught exception during a turn lands a closing error entry`.
- Attention: `maybe-notify-turn-failed!`.

Verified: `bb spec` 1379/0 (3 pending @real), `bb features features/session/error_handling.feature` 6/0.
