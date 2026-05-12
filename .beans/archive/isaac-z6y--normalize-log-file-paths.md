---
# isaac-z6y
title: "Normalize log file paths"
status: completed
type: bug
priority: normal
created_at: 2026-04-09T00:23:14Z
updated_at: 2026-04-09T00:51:04Z
---

## Description

Log entries should use consistent relative source file paths instead of a mix of relative and absolute paths.

## Scope
- Normalize the  value in log entries before writing them
- Strip workspace-specific absolute prefixes when present
- Produce a stable relative source path such as 
- Preserve compatibility with existing call sites using logging macros
- Add unit coverage for path normalization

## Notes
- This is a logging readability/consistency cleanup
- No Gherkin feature is required unless we later decide path format is part of an acceptance-level contract

