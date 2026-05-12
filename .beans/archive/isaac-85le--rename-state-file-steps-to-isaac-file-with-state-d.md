---
# isaac-85le
title: "Rename 'state file' steps to 'isaac file' with state-dir-relative paths"
status: completed
type: task
priority: low
created_at: 2026-04-23T15:54:10Z
updated_at: 2026-04-28T03:10:50Z
---

## Description

Current step vocabulary uses 'state file' which is confusing. Rename to 'isaac file' and let paths be relative to the state directory so scenarios write e.g. "config/crew/marvin.edn" instead of "target/test-state/config/crew/marvin.edn".

Affected steps:
- spec/isaac/features/steps/server.clj:270  the EDN state file "X" contains:
- spec/isaac/features/steps/server.clj:292  the EDN state file "X" does not exist
- spec/isaac/features/steps/cli.clj:314     the state file X exists
- spec/isaac/features/steps/cli.clj:322     the state file X contains:
- any other 'state file' variant found

Do:
- Rename to 'isaac file' / 'EDN isaac file' variants
- Path resolution: treat as relative to state-dir when not absolute
- Update all feature files that use the old phrasing
- Keep existing absolute-path behavior working (if path starts with /)

Acceptance:
1. grep -rn 'state file' features/ spec/ returns no matches
2. bb features passes
3. bb spec passes

## Notes

Verification failed: full suites pass and 'state file' is gone, but spec/isaac/features/steps/cli.clj:364 still prefixes absolute paths with state-dir in the isaac file contains step, so paths that start with / are no longer honored.

