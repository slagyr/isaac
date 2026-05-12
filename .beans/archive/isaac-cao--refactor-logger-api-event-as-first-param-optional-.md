---
# isaac-cao
title: "Refactor logger API: event as first param, optional braces"
status: completed
type: task
priority: low
created_at: 2026-04-10T03:48:05Z
updated_at: 2026-04-10T03:58:26Z
---

## Description

The logger macros currently take a single map with :event buried inside:
  (log/debug {:event :chat/response :provider provider :model model})

Two improvements:
1. Make :event the first positional parameter — it's always required
2. Make the curly braces optional — accept varargs as key-value pairs

After:
  (log/debug :chat/response :provider provider :model model)

This touches every log call site in the codebase. Remove the four TODO comments in logger.clj when done.

Definition of Done:
- log*, build-entry, and all macros (error, warn, report, info, debug) take event as first param
- Remaining args accepted as varargs (no braces required)
- All call sites updated
- TODO comments in logger.clj removed
- bb features and bb spec pass

## Acceptance Criteria

1. All log call sites use the new API: (log/level :event/name :key val ...)
2. No {:event ...} maps remain in log calls
3. Four TODO comments in src/isaac/logger.clj removed
4. bb features and bb spec pass

