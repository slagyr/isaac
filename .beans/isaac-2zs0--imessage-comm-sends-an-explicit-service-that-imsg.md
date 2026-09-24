---
# isaac-2zs0
title: iMessage comm sends an explicit service that imsg cannot fulfil; default should be auto
status: completed
type: bug
priority: high
created_at: 2026-09-24T18:04:20Z
updated_at: 2026-09-24T18:28:54Z
---

## Problem

`isaac.comm.imessage/imsg-params` passes `:service` through to imsg whenever
the comm slice sets `:imessage/service`:

```clojure
(cond-> {:to (:imessage/target record) :text (:content record)}
        (:imessage/service record)
        (assoc :service (str/lower-case (:imessage/service record))))
```

Both hosts were configured `:imessage/service "iMessage"` — the obvious value,
and the one the berth's own docs suggest. Measured on zanebot 2026-09-24
against a handle with an established 560-message iMessage chat:

| call | result |
|---|---|
| `imsg send --to micahmartin@mac.com --text … --service imessage` | **fails** — "Messages automation returned success, but no matching outgoing text row was observed within 8 seconds" |
| `imsg send --to micahmartin@mac.com --text … --service auto` | **sent** |
| `imsg send --chat-identifier micahmartin@mac.com --text …` | **sent** |

So the explicit-service path is the broken one, and Isaac was the only thing
choosing it. Every send from both hosts failed this way, reported as the
ambiguous `-32001 "Delivery outcome unknown"`, and was then retried (see
isaac-fkjq). zanebot's own iMessage alerts had been dead since 2026-09-21 for
this reason on top of a signed-out account; yopp's brand-new comm never sent
at all until `auto` was set.

Fixed in place on both hosts by setting `:imessage/service "auto"` — a live
send then delivered first attempt (`:comm.delivery/delivered :attempts 0`).
That is a config workaround, not the fix.

## Acceptance

- The comm does not send an explicit `:service` unless the operator set one
  deliberately; absent config behaves as `auto`, not as `imessage`.
- The berth's schema documents the accepted values (`imessage` / `sms` /
  `auto`) and that `auto` is the working default. If `imessage` is genuinely
  unusable through imsg's AppleScript transport, say so rather than offering it.
- A scenario covers the default: a slice with no `:imessage/service` produces
  send params with no `:service` key.

## Exceptions

- Whether imsg's explicit-`imessage` transport is itself buggy is imsg's
  problem, not Isaac's. Isaac's part is to stop choosing it by default.
