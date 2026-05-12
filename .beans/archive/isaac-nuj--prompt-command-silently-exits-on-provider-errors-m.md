---
# isaac-nuj
title: "prompt command silently exits on provider errors — must print to stderr"
status: completed
type: bug
priority: high
created_at: 2026-04-15T17:10:53Z
updated_at: 2026-04-15T18:16:26Z
---

## Description

When a provider returns an error (quota, connection refused, etc.), the prompt command returns exit code 1 but prints nothing. The user sees no output and has no idea what went wrong. The error is only in the transcript and logs.

Line 89-90 in cli/prompt.clj detects the error but just returns 1.

features/cli/prompt.feature — 1 @wip scenario (updated existing)

## Acceptance Criteria

@wip scenario passes with @wip removed. Provider errors print to stderr.

