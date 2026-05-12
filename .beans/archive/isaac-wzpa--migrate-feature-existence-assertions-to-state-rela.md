---
# isaac-wzpa
title: "Migrate feature existence assertions to state-relative step"
status: completed
type: task
priority: low
created_at: 2026-04-22T18:23:10Z
updated_at: 2026-04-24T00:31:26Z
---

## Description

Introducing 'the state file "<relpath>" exists' (via isaac-ua1n) gives us a state-relative positive-existence assertion. Existing features use 'a file "<name>" has content "<X>"' or absolute-path checks where existence is all that matters.

Migration scope:
- Find features asserting existence via the content-step when only existence matters; switch to 'the state file "<relpath>" exists'
- Find features using absolute paths like 'target/test-state/...'; switch to state-relative
- Remove any unused file-existence step-defs after migration

Blocked on isaac-ua1n (introduces the new step).

Small housekeeping bead; not blocking anything.

