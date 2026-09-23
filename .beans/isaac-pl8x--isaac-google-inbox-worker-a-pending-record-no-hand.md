---
# isaac-pl8x
title: 'isaac-google inbox worker: a pending record no handler claims is re-read every 2 s and warns :google/handler-missing forever — park it once; the smoke probe gets a no-op handler'
status: todo
type: bug
priority: high
tags:
    - google
    - ops
created_at: 2026-09-23T13:23:51Z
updated_at: 2026-09-23T13:23:51Z
---

## Observed (yopp, 2026-09-23 03:00–03:22Z)

`isaac google smoke --send-live` published probe 20295779321476340 (type isaac.google.smoke/probe). The door accepted it into inbox/pending and the smoke reported PASS. From then on the inbox worker (2 s cadence) logged `:google/handler-missing` for that record on every tick — about 1,800 warnings an hour — because no handler claims that type and an unhandled record stays pending. Stopped by hand: the record was moved to google/inbox/unhandled/.

## Change

- Worker: a record whose type has no handler is moved to `inbox/unhandled/` on first sight with ONE warning (`:google/handler-missing` with type and id), never re-read. `isaac google status` and the smoke's inbox check report the unhandled count.
- The smoke probe type gets a no-op handler in isaac-google (like the heartbeat: record arrival, mark done), so `--send-live` leaves nothing behind.
- Heartbeat records must never reach the inbox at all (they do not today; keep the scenario).

## Scenarios

- a pending record of an unknown type → one warning, record in unhandled/, next tick logs nothing.
- a smoke probe → handled, done/, no warning.

## Acceptance

bb spec / bb features / bb ci green in isaac-google; one-time on yopp: run the smoke with --send-live and confirm the log stays quiet afterwards.
