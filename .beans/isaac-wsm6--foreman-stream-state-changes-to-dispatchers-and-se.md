---
# isaac-wsm6
title: 'Foreman: stream state changes to dispatchers, and send stall alerts to whoever dispatched'
status: completed
type: feature
priority: high
tags:
    - foreman
created_at: 2026-11-16T15:25:41Z
updated_at: 2026-11-16T15:49:12Z
---

Repos: **isaac-foreman** (stream route, empty-target skip), **isaac-http** if the route layer needs a streaming response shape. Micah, 2026-11-16, after isaac-o4di and isaac-wh9y stalled unseen until the planner's minute-poll caught them. Specs: `features/foreman/stream.feature`, `features/foreman/dispatcher_alert.feature`.

## Problem

- A stall alert today is a turn for crew `prowl` in session `plan-<instance>` with no comm, so it reaches nobody. Micah: the planner-crew juggle did not pan out; alerts go to **the agent that dispatched the bean**.
- Dispatchers outside Isaac (the planner in Claude Code) can only poll, so stalls are seen late and states between polls are missed.

## What

1. **Stream.** `GET /foreman/stream?machine=<id>[&instance=<id>]`, scope `:foreman/read`, answers `text/event-stream`. On connect: one `{type "state", machine, instance, state}` event per live instance. Then one `{type "transition", machine, instance, id, event, from, to, reason, at}` per transition as it is applied, whichever door the event came through. The CLI signals from another process in the scenarios, so the stream must follow the instance store (events/transition records), not an in-process callback. Heartbeat comments keep idle connections open; the server's shutdown closes them.
2. **Alerts to the dispatcher.** Already config: a machine's alert action targets `{:session "{{data.alert.session}}"}` and the dispatcher starts the instance with `data.alert.session` (the first dispatcher_alert scenario passes today as a guard). **Code:** a `:turn` action whose rendered session is empty starts no turn and records `alert skipped: no target session` (generally `<action> skipped: no target session`) in the instance history.
3. **New steps** (gherclj, in this repo's feature steps): `a client opens a stream to "<path>":` (same key/value table as `a POST request is made to`, header rows; a refused stream sets the last response so `the response status is <n>` works), `the stream delivers an event matching:` (waits with a timeout for a JSON event whose keys match), `the stream has delivered no event matching:`.

## Deploy (planner, after landing; not part of the gate)

- zanebot `config/machines/isaac-bean-work.edn` `:alert`: `:frequencies {:session "{{data.alert.session}}"}` and drop the prowl crew/session. `tono-bean-work` unchanged for now.
- Dispatchers that are Isaac sessions pass `"data": {"alert": {"session": "<their session>"}}`; the planner in Claude Code instead runs a Monitor on `curl -N .../foreman/stream?machine=isaac-bean-work`.

## Acceptance

`bb features features/foreman/stream.feature features/foreman/dispatcher_alert.feature` green with `@wip` removed (7 scenarios), plus `bb spec` and full `bb features` green. Do not bump the module version (the deploy train does).

feature-baseline: isaac-foreman 9abcf3e1ebd1116ca2694e1da8b36a3e44bedc2d
feature-blob: isaac-foreman features/foreman/stream.feature a9500bc5265c03f364b46889a8fd41ad34f2d489
feature-blob: isaac-foreman features/foreman/dispatcher_alert.feature ae36ac2dda0badf04d53cd9b6e178db1f7a45646

## Checkpoint (2026-11-16)

Done: stream route follows persisted transition history across processes; dispatcher alert skips empty session; new stream feature steps. Focused 7 scenarios, 108 specs, 69 full features and `bb ci` green; gate PASS on squash 9ddbd24e6e7c190607138db0056feed9e0cfd8fc. Branch bean/isaac-wsm6 pushed at 43ad223.
Next: push the verified squash commit from `../isaac-foreman-wsm6-land` to `isaac-foreman` main, record main-sha and complete the bean. Resume at `../isaac-foreman-wsm6-land/src/isaac/foreman/stream.clj:35` if rework is required.

## Landed on main (2026-11-16)

main-sha: isaac-foreman 9ddbd24e6e7c190607138db0056feed9e0cfd8fc
