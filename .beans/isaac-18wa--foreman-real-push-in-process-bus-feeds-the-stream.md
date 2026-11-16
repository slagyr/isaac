---
# isaac-18wa
title: 'Foreman: real push — in-process bus feeds the stream, no polling; foreman and dream commands are server-only'
status: completed
type: feature
priority: high
tags:
    - foreman
created_at: 2026-11-16T18:29:27Z
updated_at: 2026-11-16T19:13:14Z
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

feature-baseline: isaac-foreman 8e50eaa6229e2e4d146827635690b51488e04eab
feature-blob: isaac-foreman features/foreman/stream.feature 20648c283d2eba2c4b178ab63347c9da80e39ce5 116,141

## Planner note (2026-11-16): foundation pin

isaac-dcxk landed: pin isaac-foundation (deps.edn and bb.edn, including the spec/test-support roots) to `ab9f1c5711c7f85aefc67100bdaef1b80fe7a736`.


## Checkpoint (2026-11-16)

Done: Foreman bus publishes persisted records, stream subscribes with fan-out/heartbeat and no polling; added second-stream steps and foundation pin; dream worktree created. Next: fix stream HTTP route registration (currently GET /foreman/stream returns 404); inspect feature-steps/isaac/foreman_stream_steps.clj:24 and module discovery/route registration in server test harness, then run bb features features/foreman/stream.feature. Last test red: 7 examples, 6 failures, 8 assertions (HTTP 404), bb spec --focus spec/isaac/foreman/store_spec.clj green but logged expected broken watcher. No implementation checkpoint commit because acceptance red.


## Landed on main (2026-11-16)

main-sha: isaac-foreman 2f154c687504547415e6e0761da218b0431aff04
main-sha: isaac-dream 4af47a2a19b7a14510b3b84c70fe56e3e2439fcd

Acceptance: Foreman bb ci (109 specs, 71 features), stream feature (7 scenarios), Dream bb ci (57 specs, 78 features), bean-gate PASS after squash.
