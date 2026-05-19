---
# isaac-pu2x
title: Delivery worker honors transient? false from Comm/send!
status: draft
type: bug
created_at: 2026-05-19T21:14:47Z
updated_at: 2026-05-19T21:14:47Z
---

## Gap
`isaac.comm/Comm` protocol's `send!` is documented to return
`{:ok false :transient? bool}`, where `:transient? false` signals a
permanent failure that should not be retried. The current
`isaac.comm.delivery.worker` ignores `:transient?` — every failure
goes through the full 5-attempt backoff before dead-lettering.

## Impact
On a permanent failure (e.g., unknown iMessage buddy, invalid
Discord channel id), the worker wastes 5 send attempts spread over
~13 minutes before moving the record to `failed/`. Cost is small
per record but compounds with operator delay before review.

## Proposed change
`src/isaac/comm/delivery/worker.clj`, `process-record!` /
`reschedule!`: when `(:transient? result)` is `false`, call
`queue/move-to-failed!` immediately instead of incrementing
attempts and rescheduling. Continue to log
`:delivery/dead-lettered` with reason.

## Surface
- worker change ~5 lines
- new feature scenario in `features/delivery/queue.feature` for
  the permanent-failure short-circuit; existing
  transient/dead-letter scenarios stay valid

## Origin
Surfaced while planning iMessage outbound (`isaac-imessage`).
MVP will return `:transient? true` for everything until this
lands.
