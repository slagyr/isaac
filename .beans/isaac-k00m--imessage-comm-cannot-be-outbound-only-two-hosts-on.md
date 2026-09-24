---
# isaac-k00m
title: iMessage comm cannot be outbound-only; two hosts on one chat.db both answer
status: completed
type: feature
priority: high
created_at: 2026-09-24T18:17:05Z
updated_at: 2026-09-24T19:20:59Z
---

## Problem

`subscribe-to-inbound!` is called unconditionally from `on-load`
(`src/isaac/comm/imessage.clj`). Any host that configures an iMessage comm
watches the chat.db it points at — there is no way to declare a send-only comm.

yopp's comm reaches zanebot's `imsg` over SSH and therefore watches **the same
chat.db zanebot already watches**. Confirmed live 2026-09-24:

    yopp    :imsg.watch/subscribed  :subscription 1
    zanebot :imsg.watch/subscribed  (its own comm, same db)

So one inbound message from the operator dispatches a turn on **both** hosts and
produces two independent replies. The operator caught this before it fired; it
was not designed for.

The berth offers no `:imessage/inbound?` or equivalent. The only lever is
`:imessage/allow-from`, and only because `allowed?` treats an empty list as
"nobody":

```clojure
(cond
  (nil? allow-from) true                       ; absent → everyone
  (some #(= % handle) allow-from) true
  :else false)                                 ; [] → nobody
```

**Worked around in place** on yopp by setting `:imessage/allow-from []`. That
works — `notification->work-item` returns nil and the handler does nothing, with
no side effects — but it reads as a mistake rather than as intent, and it still
opens the subscription and pays for every notification.

## Acceptance

- A comm can declare itself send-only, and then does not call
  `watch.subscribe` at all — the intent is legible in config and costs nothing
  at runtime.
- An absent flag keeps today's bidirectional behaviour.
- yopp's `:imessage/allow-from []` workaround is replaced by the real flag, and
  its allow-from restored to the operator's handle (it is the *sender*
  whitelist, not an inbound switch).
- A scenario covers a send-only slice: a send delivers, and an inbound
  notification dispatches nothing.

## Notes

Worth considering separately: two comms watching one chat.db is a hazard the
platform cannot see. A warning when two hosts subscribe to the same db is out
of scope here (they cannot observe each other), but a note in the berth
description that the watch is db-wide would have prevented this.

feature-baseline: isaac-imessage 9cdf858b77b1ff5c3f9cc20576bfb9afb604e31c
feature-blob: isaac-imessage features/comm/imessage/outbound_only.feature f84c8b5d0efba5cd06af972dbc74b62739a4dc9f

## Landed on main (2026-09-24)

main-sha: isaac-imessage 572eafaf174d58c17332de6a6021079f7dd706e5

What shipped:

- `:imessage/inbound? false` makes a comm send-only. `on-load` (and the
  reconnect path) call `subscribe-unless-send-only!`, which skips
  `watch.subscribe` and logs `:imsg.watch/send-only`. Absent or `true` keeps
  the bidirectional behaviour.
- `notification->work-item` returns nil on a send-only slice
  (`:imessage.intake/send-only` at debug), so even a notification that arrives
  some other way dispatches nothing.
- Berth schema: `:imessage/inbound?` (`:boolean`) documented as the inbound
  switch, with `:imessage/allow-from` named as the sender whitelist it is. The
  `:imessage/db-path` description now states the watch is database-wide and
  that all but one host sharing a db needs `:imessage/inbound? false` — the
  note the Notes section asked for.
- Suites: `bb ci` green (63 native, 72 JVM specs, 23 feature scenarios).
  Both mutations of the new predicate (always-inbound, never-inbound) were
  checked to fail the new scenarios before the real one was restored.

Two test-quality fixes the work uncovered, both in this repo's specs:

- `imessage_spec.clj` wrapped eight `it`s in a `let` inside `describe`; a let
  yields only its last form, so seven of them had never run. Fixtures moved to
  top level — the suite went 55 → 63 examples, all green.
- The `the polled work items are:` step asserted nothing when handed a
  header-only table (match-entries iterates rows). It now also asserts item
  count == row count, so this bean's empty-table scenario is real.

## Operator follow-up (not reachable from this repo)

The third acceptance bullet is host config on **yopp**, not code: replace
`:imessage/allow-from []` with `:imessage/inbound? false` and restore
allow-from to the operator's handle. yopp's `~/.isaac/config` is not on this
machine (zanebot's copy already carries the real allow-from), so it needs the
operator's hand. The flag it needs now exists and hot-reloads.
