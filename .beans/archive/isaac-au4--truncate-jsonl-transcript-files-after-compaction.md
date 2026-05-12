---
# isaac-au4
title: "Truncate JSONL transcript files after compaction"
status: completed
type: task
priority: low
created_at: 2026-04-10T03:20:39Z
updated_at: 2026-04-10T21:24:48Z
---

## Description

Session transcript files grow without bound — compaction appends a summary entry but never removes the old messages. Every tool result, message, and compaction entry stays in the file forever.

OpenClaw handles this with truncateSessionAfterCompaction() which physically rewrites the JSONL file after compaction: removes summarized message entries before firstKeptEntryId, re-parents orphaned entries, keeps the session header and non-message state.

Isaac should do the same — after a successful compaction, rewrite the transcript to remove entries that have been summarized away. The full history is in git if needed.

Reference: /Users/micahmartin/Projects/openclaw/src/agents/pi-embedded-runner/session-truncation.ts

