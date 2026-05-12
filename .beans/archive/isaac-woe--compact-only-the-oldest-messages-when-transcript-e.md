---
# isaac-woe
title: "Compact only the oldest messages when transcript exceeds context window"
status: completed
type: bug
priority: high
created_at: 2026-04-09T03:46:19Z
updated_at: 2026-04-09T04:56:30Z
---

## Description

compact! sends ALL conversation messages to the LLM for summarization. When the accumulated transcript is larger than the model's context window, the summarization call fails. This compounds with the silent error bug to create an unrecoverable infinite compaction loop.

The fix is to select only the oldest messages that fit within the model context window for the compaction call, and preserve the remaining (newer) messages in the transcript after the compaction entry.

## Feature
features/context/compaction.feature — 'Compaction targets only the oldest messages when history exceeds the model context window'

## Root Cause
- compact! calls prompt/estimate-tokens on all messages, then sends all messages (context/manager.clj ~line 25-36)
- No slice/chunk logic exists to limit the compaction prompt to the context window

## Fix
- In compact!, estimate token count of the full message list
- If estimated tokens exceed context-window, take only the oldest messages that fit
- The remaining (newer) messages are NOT included in the compaction prompt
- After compaction, the transcript should have: [compaction-entry] [preserved-newer-messages] [new-user-message]
- Update storage/append-compaction! or compact! to correctly set firstKeptEntryId to the first preserved (not compacted) message

## Definition of Done
- @wip removed from compaction.feature 'Compaction targets only the oldest messages' scenario
- bb features passes
- bb spec passes

