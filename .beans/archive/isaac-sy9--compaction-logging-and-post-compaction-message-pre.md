---
# isaac-sy9
title: "Compaction logging and post-compaction message preservation"
status: completed
type: bug
priority: high
created_at: 2026-04-08T22:20:53Z
updated_at: 2026-04-09T01:07:11Z
---

## Description

Context compaction should be observable and should preserve the new user message that triggered compaction.

## Scope
- Log compaction checks with session, provider, model, totalTokens, and contextWindow
- Log when compaction starts
- Ensure normal chat-triggered compaction preserves the new user message after the compaction entry
- Ensure chat still completes after compaction
- Add feature/spec coverage for compaction logging and post-compaction transcript order

## Feature direction
- Trigger compaction through normal chat behavior with a small context window
- Use one log matcher table to assert both the compaction-check and compaction-started entries
- Use transcript matching with #index to assert the compaction entry precedes the newly sent user message

## Notes
- This should help diagnose repeated or unexpected compactions in CLI chat
- Acceptance tests should exercise real chat flow, not a one-off compaction-only test seam

