---
# isaac-pu2x
title: Delivery worker honors transient? false from Comm/send!
status: todo
type: bug
created_at: 2026-05-19T21:14:47Z
updated_at: 2026-05-19T23:00:00Z
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
attempts and rescheduling.

Dead-letter log gains a `:reason` field to distinguish paths:
- `:reason :permanent` — single-tick short-circuit on
  `:transient? false`
- `:reason :exhausted` — hit the 5-attempt max

Both paths continue to emit `:delivery/dead-lettered` at `:error`.

## Surface

### Worker
- ~5 lines in `src/isaac/comm/delivery/worker.clj` to short-circuit
  on `:transient? false`, plus `:reason` on the two
  `:delivery/dead-lettered` log sites.

### Feature-level stub comm
- `spec/isaac/server/server_steps.clj` `StubComm` drops its
  `bb-http/post` path. It now records every `send!` call and
  returns a configured result. The `with-http-post-stub` wrapper
  is removed from the delivery worker step definitions.
- Default return when unconfigured: `{:ok true}`.

### New step definitions
- `Given the comm "stub" returns: <table>` — configures the
  stub's return value for all subsequent `send!` calls. Sticky
  until re-invoked. Table columns are the keys of the result map
  (`ok`, `transient?`, etc.).
- `Then the comm "stub" was called with: <table>` — asserts the
  stub received at least one `send!` whose record matches the
  given fields (target, content, etc.).

### Feature scenarios — `features/delivery/queue.feature`
Migrate all three existing scenarios to the new vocabulary in
this bean (no mixed mechanisms):
- **Success**: configure `{:ok true}`; assert pending file
  removed; assert stub was called with the expected target +
  content.
- **Transient retry**: configure `{:ok false :transient? true}`;
  assert attempts incremented and `next-attempt-at` set per the
  backoff schedule.
- **Dead-letter (exhausted)**: configure
  `{:ok false :transient? true}` from `attempts 4`; assert
  moved to `failed/` with log `:reason :exhausted`.

Plus the new scenario:
- **Permanent failure**: configure
  `{:ok false :transient? false}` from `attempts 0`; assert
  moved to `failed/` after a single tick with log
  `:reason :permanent`.

### Unit-test coverage
`spec/isaac/comm/delivery/worker_spec.clj`:
- Add a case for `(->StubComm {:ok false :transient? false})`
  asserting the record lands in `failed/` after a single tick
  with `attempts 0` and the log carries `:reason :permanent`.
- Update the existing "moves a delivery to failed and logs when
  it reaches max attempts" case to also assert
  `:reason :exhausted`.

## Origin
Surfaced while planning iMessage outbound (`isaac-imessage`).
MVP will return `:transient? true` for everything until this
lands.
