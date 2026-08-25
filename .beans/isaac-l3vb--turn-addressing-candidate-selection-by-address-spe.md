---
# isaac-l3vb
title: 'Turn addressing: candidate selection by address spec; hail router contracts onto it'
status: draft
type: feature
priority: normal
created_at: 2026-08-25T18:57:47Z
updated_at: 2026-08-25T18:57:47Z
blocked_by:
    - isaac-ohsy
---

Likely repos: **isaac-agent** (selection in core) then **isaac-hail** (router
cutover). Second half of the turn-request queue (2026-08-24 architecture
session; split out of isaac-ohsy on 2026-08-25).

## Problem

Hail's router (isaac-hail `router.clj`) owns "pick one session" by
`:session | :session-tags | :crew | :reach | :prefer | :create`. Foreman's
`:turn` action and worksite pools (W2) need the same algorithm without
depending on hail. The queue core (isaac-ohsy) parks and wakes a request
bound to ONE session; this bean lets a request name a SET.

## Design

- `submit` accepts an address spec `{:session | :crew | :tags, :reach}` in
  place of a session key; resolution happens in core at admit time: candidates
  = sessions matching the spec; run the first (stable order) whose whole
  turnstile stack passes; if none pass, hold the request (queue core semantics)
  and re-resolve on every wake.
- `:reach :one` (default) = one candidate runs; `:create :if-missing` mirrors
  hail's create policy.
- Hail's router becomes naming + records over this: bands resolve to an
  address spec and submit; deferral-on-`:session-in-flight` is replaced by the
  queue's hold. Hail keeps thread/records, deferral's human semantics
  (attention), comm reach.

## Scenarios (to draft before todo)

- address `{:tags [...]}` with two candidates, one behind a closed turnstile:
  the free one runs.
- both candidates busy → held; ending one turn (token) runs the request there.
- `:reach :one` + no candidate + `:create :if-missing` creates and runs.
- hail band with `:session-tags` delivers through the queue: hail's own
  deferral path is not taken (log shows the queue hold, not
  `:hail/delivery-deferred`).

## Notes

- Stays draft until scenarios exist. No hail↔foreman arrows either way.
