---
# isaac-v557
title: 'Foreman over HTTP: start, list and read instances with scoped principals'
status: in-progress
type: feature
priority: high
tags:
    - foreman
created_at: 2026-10-28T08:20:37Z
updated_at: 2026-10-28T08:32:57Z
---

Micah, 2026-10-28: "We need to be able to interact with Foreman over HTTP." Today a dispatcher off the host can signal an existing instance (POST /foreman/events) and set its data, but cannot start one or read its status, and both existing routes require the `*` scope, so the hail principal gets 403. Starting a bean on Foreman therefore needs ssh to zanebot.

## Contract (isaac-foreman)

- `POST /foreman/instances` with `{machine, instance, data?, event?}` starts the instance (201, body has `state`). With `event`, it also fires that event in the same call, so one request dispatches a bean. A second start of the same instance is 409; an unknown machine is 404.
- `GET /foreman/instances/<machine>/<id>`: `state`, `since`, `data`, `pending`, and `history` (each entry has `event`, `from`, `to`, `source`). Unknown instance is 404.
- `GET /foreman/instances/<machine>`: `{instances: [{id, state, since}]}`, sorted by id.
- Scopes: `:foreman/write` for the three POST routes (start, events, data); `:foreman/read` for the GETs. Declared as `:scope` on each route in the manifest.

JSON by default, EDN when the request says `application/edn`, like the existing routes.

## After it lands (planner)

Deploy, then add `:foreman/write` and `:foreman/read` to the dispatching principal on zanebot (Micah's call which principal), and document the two curl calls next to the hail ones in AGENTS.md.

## Acceptance

`cd isaac-foreman && bb features features/foreman/http.feature` (six scenarios), and the rest of `bb ci` stays green.

feature-baseline: isaac-foreman 54440fedfdb745e52e192b4686eb11b07d4d694e
feature-blob: isaac-foreman features/foreman/http.feature 7433a6984dd52bc57a6959dff9bf2680017ba00f 22,37,52,70,89,103
