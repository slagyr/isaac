---
# isaac-ays
title: "Readable log timestamp and key ordering"
status: completed
type: feature
priority: high
created_at: 2026-04-08T21:39:02Z
updated_at: 2026-04-08T21:58:30Z
---

## Description

Improve the default readability of structured EDN log output without introducing user-configurable formatting.

## Scope
- Change log timestamps from epoch milliseconds to ISO-8601 UTC strings
- Keep log entries as single-line EDN maps
- Ensure printed log lines place :ts, :level, and :event first in that order
- Preserve structured context fields after the core keys
- Add feature/spec coverage for timestamp format and printed key ordering

## Non-goals
- No local-time formatting option yet
- No configurable formatting profiles
- No JSONL output as part of this bead

## Notes
- This is a readability default, not a general logging customization system
- Prefer a stable house style for logs over user-selectable formatting

