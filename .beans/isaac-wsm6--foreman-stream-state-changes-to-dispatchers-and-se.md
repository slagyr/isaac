---
# isaac-wsm6
title: 'Foreman: stream state changes to dispatchers, and send stall alerts to whoever dispatched'
status: draft
type: feature
priority: high
tags:
    - foreman
created_at: 2026-11-16T15:25:41Z
updated_at: 2026-11-16T15:25:41Z
---

Repos: **isaac-foreman** (routes, alert routing), **isaac-http** (streaming response support). Micah, 2026-11-16, after the dream batch where isaac-o4di and isaac-wh9y stalled and nobody saw it until the planner's minute-poll did.

## Problem

- A stall alert today is a turn for crew `prowl` in session `plan-<instance>` ("tell Micah what you found"). That session has no comm, so the report reaches nobody. Micah: the planner-crew juggle did not pan out; alerts should go to **the agent that dispatched the bean**.
- Dispatchers outside Isaac (the planner in Claude Code) can only poll `GET /foreman/instances/...`, so a stall is seen a minute or more late, and states visited between polls are missed.

## Direction

1. **Event stream.** `GET /foreman/stream?machine=<id>` (optionally `&instance=<id>`) answers `text/event-stream` and pushes one event per transition as it happens: `{machine, instance, event, from, to, reason, at}`. Same bearer auth and scope as `foreman/read`. A heartbeat comment keeps idle connections open. A dispatcher runs `curl -N` on it (the planner's Monitor) right after `POST /foreman/instances`.
   - isaac-http has no streaming routes today; it needs a handler shape that can hold a response open and write to it (server-sent events), honoring the server's shutdown.
2. **Alerts go to the dispatcher.** `POST /foreman/instances` takes an optional `alert` target, recorded on the instance:
   - `{"session": "<id>"}` (an Isaac crew session that dispatched) → the stall alert is a turn in that session.
   - absent → no alert turn; the transition still goes out on the stream, and one plain line (no model) goes to the `attention.notify` target: `<instance> stalled: <reason> — resume with dispatch`.
   - The machine's static `:alert` (prowl / `plan-{{instance}}`) is removed from `isaac-bean-work`.

## Open questions

- Replay: should the stream send current states on connect (so a watcher that starts late sees a stall that already happened)? Probably yes, one snapshot event per live instance.
- Does `tono-bean-work` (alerts `tono-plan-1`) move to dispatcher routing too, or keep its static alert?

## Acceptance (draft)

- Scenario: a client streaming a machine receives a transition event, with reason, when an instance stalls.
- Scenario: an instance dispatched with `alert.session` gets its stall alert as a turn in that session.
- Scenario: an instance dispatched without an alert target sends one notify line on stall and starts no turn.
- Scenario: the stream refuses a token without `foreman/read`.
