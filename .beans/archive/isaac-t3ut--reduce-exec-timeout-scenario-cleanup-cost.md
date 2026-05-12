---
# isaac-t3ut
title: "Reduce exec timeout scenario cleanup cost"
status: completed
type: task
priority: normal
created_at: 2026-04-27T17:48:38Z
updated_at: 2026-04-27T17:57:23Z
---

## Description

The built-in exec tool timeout scenario is one of the slowest remaining passing feature cases. The timeout path currently destroys the process and then waits up to 100ms before forcing termination. Tighten that cleanup path so timed-out exec tests finish faster without changing the user-visible timeout behavior.

## Acceptance Criteria

1. The exec timeout path uses a cheaper post-timeout cleanup strategy. 2. The built-in timeout feature scenario remains correct. 3. bb features and bb spec pass. 4. The timeout scenario becomes measurably faster.

## Notes

Follow-up from suite timing analysis after ACP and server speedups.

