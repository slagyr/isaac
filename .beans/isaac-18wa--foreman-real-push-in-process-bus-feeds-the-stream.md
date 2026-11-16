---
# isaac-18wa
title: 'Foreman: real push — in-process bus feeds the stream, no polling; foreman and dream commands are server-only'
status: draft
type: feature
priority: high
tags:
    - foreman
created_at: 2026-11-16T18:29:27Z
updated_at: 2026-11-16T18:29:27Z
blocked_by:
    - isaac-dcxk
---

Repos: **isaac-foreman** (bus, stream, flag) and **isaac-dream** (flag only). Bean C of three; needs isaac-dcxk (the `:server-only` flag). isaac-w0yx makes the flag route commands typed on the host into the server. Spec: `features/foreman/stream.feature`, the two new `@wip` scenarios at the end.

## Problem

isaac-wsm6's stream fakes push: each open stream runs a thread that, every 200 ms, lists every instance of the machine, re-reads each instance's event file and sends a heartbeat (~300 file reads/s per watcher on zanebot today, growing with every bean). Errors end the thread silently. Micah, 2026-11-16: "If we're polling it 5 times a second, that sounds terrible."

## What

- **Bus.** Every Foreman write in the server process (receive/transition/action records in `isaac.foreman.store`, whichever door) publishes the record to an in-process bus right after it is persisted. The stream subscribes on connect, sends the live-instance snapshot once, then forwards bus events for its machine (and instance filter). **No polling thread and no re-reading of event files** after the snapshot.
- **Fan-out and cleanup.** Any number of watchers; each gets each transition once. A closed or broken connection unsubscribes; it never breaks a writer or other watchers.
- **Heartbeat** every 15 s (comment line). Stream errors are logged (`:foreman.stream/error` with machine and cause), never swallowed.
- **Server-only.** Mark the `foreman` CLI entry `:server-only true` (isaac-foreman manifest) and the `dream` entry `:server-only true` (isaac-dream manifest), so with isaac-w0yx every Foreman write and every dream run typed on the host happens in the server and reaches the bus.
- **New steps:** `a second client opens a stream to {path}:`, `the second stream delivers an event matching:`, `the {stream|second stream} has delivered exactly {n} event(s) matching:`, `the client closes the stream`.

The scenarios check fan-out and cleanup; removing the polling loop is checked in review (`isaac.foreman.stream` has no sleep loop and does not call `store/history` after the snapshot).

## Acceptance

`bb features features/foreman/stream.feature` green with the two `@wip` tags removed (7 stream scenarios), plus `bb spec` and full `bb features` green in isaac-foreman, and isaac-dream's suites still green with the flag. Pin both to isaac-dcxk's foundation sha. Do not bump versions (the deploy train does).
