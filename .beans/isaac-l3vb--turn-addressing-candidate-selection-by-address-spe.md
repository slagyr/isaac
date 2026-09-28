---
# isaac-l3vb
title: Agent turn addressing and compatible resource-pool selection
status: todo
type: feature
priority: normal
created_at: 2026-08-25T18:57:47Z
updated_at: 2026-09-28T01:32:07Z
parent: isaac-q3u3
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
- At admission Agent considers session candidates and pool leases together; the all-or-nothing lease acquisition and rollback are isaac-ey7a's contract, used here, not re-specified. Re-evaluate candidates on each wake rather than binding a busy session prematurely.
- **Sessions are independent of resources** (Micah, 2026-09-27: the session/work-dir mapping was a forced coupling). Session candidates are chosen by the session pattern alone; no session is pinned to a directory and no session is ineligible because of its past cwd.
- A lease receipt supplies declarative bindings and an opaque release identity. `:session/cwd` is a **per-turn** binding applied before the charge is built — for a new or an existing session alike. Boot files are read from the turn's cwd, and the transcript records which cwd each turn ran in. Do not patch a resolved charge after the drive has read the session context.
- Preserve direct-session and `:reach :one` semantics; creation policy and ordering must be stated in scenarios. `:reach :all` fan-out remains a separate design question until scenario review.

Revised scenario plan: free candidate wins over busy candidate; all combinations busy then wake and reselect; an existing session runs a turn at the leased cwd, reads that directory's boot files, and its transcript records the cwd; the same session's next turn runs at a different leased cwd. No Hail code or band feature belongs to this bean.


## Decisions (2026-09-27, Micah + planner) — supersede the scenario lists above

- Builds on the existing resolver `isaac.session.frequencies/resolve-session-targets` (`:session` / `:session-tags` / `:crew`, `:prefer`, `:create`, defaults merge). Today it picks one session at submission, blind to busy. isaac-asik (in progress) edits the same file — follow it.
1. **Selection moves to admission.** A request addressed by crew or tags is held without a session; each wake re-resolves: matches minus sessions already running a turn, in `:prefer` order; the first free one wins, then its pools.
2. **An explicit `:session` stays bound** and waits for that session (isaac-xoqn's waiting room).
3. **Busy is not missing:** `:create :if-missing` waits when matches exist but are all busy; `:always` still creates.
4. **Sessions and pools are checked together;** a request waiting on sessions holds no pool leases.
5. `turns list` gains a `target` column (`crew ketch`, `tags …`, or the session name).
6. `:reach :all` fan-out stays out of scope.
7. Does not depend on isaac-i5lv (receipts). Keeps isaac-ey7a and isaac-70cr (both rewrite the queue; serialize).

## Acceptance

Feature: `isaac-agent/features/turn/session_selection.feature` (new, 4 scenarios, `@wip` on main at 2e6d6d5). Remove `@wip`; all pass:

- [ ] `bb features features/turn/session_selection.feature` — `:23` free beats busy, `:40` all busy → wait, no create, takes whichever frees first, `:65` waiting request holds no pool, `:83` explicit session stays bound
- [ ] Scenarios need two `the user sends` turns in flight at once; if the step keeps only one turn future, extend its internals (same phrase).
- [ ] Existing `default_frequencies.feature` and `turn_queue.feature` stay green.
- [ ] `bb verify` green; version bump.

feature-baseline: isaac-agent 2e6d6d599e88f22e216807465f450bc8425e3bbe
feature-blob: isaac-agent features/turn/session_selection.feature 0c1f1ca5acd63dd84cfd03f23c668bdc51811da2


(2026-09-27, Micah: fan-out is killed — `:reach` is removed from frequencies entirely in isaac-5gu1. Decision 6 above is moot.)
