---
# isaac-m00v
title: "Migrate existing EDN file assertions to state-relative path step"
status: completed
type: task
priority: low
created_at: 2026-04-22T15:52:39Z
updated_at: 2026-04-22T17:23:29Z
---

## Description

Isaac-xdlg introduces a new step 'the EDN state file "<relpath>" contains:' that takes a path relative to state-dir, eliminating the 'target/test-state/' prefix duplication in scenarios.

Existing features still use the absolute-path form 'the EDN file "<abs-path>" contains:'. This bead migrates them:

- features/comm/discord/routing.feature
- features/comm/discord/reply.feature
- features/comm/discord/typing.feature
- features/comm/discord/splitting.feature

Replace each 'the EDN file "target/test-state/<relpath>" contains:' with 'the EDN state file "<relpath>" contains:'. Keep the old step-def for non-state paths OR remove it entirely if all uses are state-relative.

Small housekeeping bead; not blocking anything. Defer until isaac-xdlg lands the new step.

## Notes

Migrated Discord routing/reply/typing/splitting features to the EDN state file step. Verified with bb features and bb spec.

