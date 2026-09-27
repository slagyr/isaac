---
# isaac-l3vb
title: Agent turn addressing and compatible resource-pool selection
status: draft
type: feature
priority: normal
created_at: 2026-08-25T18:57:47Z
updated_at: 2026-09-27T22:45:05Z
blocked_by:
    - isaac-ohsy
    - isaac-ey7a
    - isaac-70cr
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

## Revision (2026-09-27, Micah + planner) — supersedes the scope above

This bean now covers **Agent candidate selection and compatible resource-pool admission only**. Hail cutover is isaac-ex4q. The durable TurnStore contract is isaac-70cr; pool type/receipt contract is isaac-ey7a; Worksite's concrete pool adapter is isaac-npmp.

- A submitted request carries session frequencies plus named `:resource-pools`. The names identify configured pool instances; no arbitrary `checkout`/`reports` aliases are required in frequencies.
- At admission Agent considers session candidates and pool leases together. Acquire all applicable leases or release acquired ones and keep the request waiting. Re-evaluate candidates on each wake rather than binding a busy session prematurely.
- A lease receipt supplies declarative bindings and an opaque release identity. `:session/cwd` is applied before opening a new session and building its charge; an existing session's pinned cwd must match. Do not patch a resolved charge after the drive has read the session context.
- Preserve direct-session and `:reach :one` semantics; creation policy and ordering must be stated in scenarios. `:reach :all` fan-out remains a separate design question until scenario review.

Revised scenario plan: free candidate wins over busy candidate; all combinations busy then wake and reselect; new session opens at selected cwd; incompatible pinned session is skipped; rollback releases a first lease when a later pool is busy. No Hail code or band feature belongs to this bean.
