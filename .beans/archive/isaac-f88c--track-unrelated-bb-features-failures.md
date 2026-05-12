---
# isaac-f88c
title: "Track unrelated bb features failures"
status: completed
type: task
priority: normal
created_at: 2026-04-23T17:26:57Z
updated_at: 2026-04-23T20:02:58Z
---

## Description

Track unrelated `bb features` failures discovered while other beads were completed. This bead does not fix those failures directly; it exists to ensure each unrelated failure family is tracked by a dedicated bead so scoped beads can still move forward honestly.

Current state (2026-04-23):
- `bb spec` passes (1019 examples, 0 failures)
- `bb features` has 1 remaining failure: ACP Tool Calls 'Tool result includes toolCallId, rawOutput, and expandable content' (UnsupportedOperationException: nth not supported on this type: PersistentArrayMap)

Follow-up beads:
- `isaac-9rdk` CLOSED — ACP proxy reconnect spec failures (resolved)
- `isaac-bpr3` UNVERIFIED — Discord routing EDN isaac file assertions (resolved)
- `isaac-m703` UNVERIFIED — Delivery queue EDN isaac file assertions (resolved)
- `isaac-xrqv` UNVERIFIED — Cron isaac file last-run/last-status assertions (resolved)
- `isaac-5776` UNVERIFIED — Config composition malformed EDN assertion (resolved)
- `isaac-32jx` OPEN — ACP tool result content shape (current sole remaining bb features failure)

## Notes

Verification failed: this tracking bead is stale again. Current workspace state is bb spec passes (1019 examples, 0 failures), but bb features now has 1 remaining failure in a different family: 'ACP Tool Calls Tool result includes toolCallId, rawOutput, and expandable content' (UnsupportedOperationException: nth not supported on this type: PersistentArrayMap). The bead still claims the sole remaining failure is config composition malformed EDN and does not reflect the current unrelated failure set accurately.

