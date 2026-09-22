---
# isaac-ihuc
title: 'One Chat subscription per tenant on spaces/-: delete per-space registration, discovery and the 30 s tick; naming by memoized spaces.get; rename follows'
status: completed
type: feature
priority: high
tags:
    - google
    - gchat
    - comm
created_at: 2026-09-22T23:26:20Z
updated_at: 2026-09-22T23:49:58Z
---

## Decision (Micah, 2026-09-22)

The Workspace Events API accepts the target `//chat.googleapis.com/spaces/-`
("all spaces for a user"; user auth only — which is how Yopp subscribes). One
subscription per tenant delivers message created/updated/deleted, reactions,
membership created/updated/deleted and read-state events for every space the
account is a member of, DMs and unnamed group chats included. Membership-created
for a DM fires after its first message, and that first message is itself pushed.
Verified against
https://developers.google.com/workspace/events/guides/events-chat on
2026-09-22. Expiry for this target was not stated on the page — assume the same
7 days as a space subscription until measured.

"Delete all that ugliness and replace it with this subscription."

## Change

- **isaac-gchat**: the tenant's registration is exactly one key,
  `spaces/-`. Delete per-space registration (`registration/space-keys`), space
  discovery and its listing/throttle (`isaac.comm.gchat.spaces`,
  `gchat/discover`, `gchat/discover-every-ms` — landed in isaac-xy2i the same
  day; remove, do not deprecate). `gchat/spaces` entries remain overrides only
  (respond policy, crew, session name); they no longer drive subscriptions and
  an unlisted space is heard because membership is the grant.
- **Naming** (keep isaac-xy2i's canon): on first sight of a space id the
  handler resolves its display name / DM member with one memoized
  `spaces.get` (+ members for a DM), builds the canonical name
  `gchat-<tenant>-<slug>` and tags the session `space:<id>`. Rename follows: when
  a later event or lookup shows a new display name, the session is renamed and
  the tag keeps it the same session (Micah: keep human-readable names).
- Membership deleted for the account → the session is left as is (history
  retention), nothing to unsubscribe.
- **isaac-google**: the registration engine keeps create/renew/delete but the
  tick cadence is no longer 30 s — one tick per hour is enough for a 7-day
  expiry (see the heartbeat bean for what else rides on it). `default-tick-ms`
  becomes hourly; the inbox worker keeps its own cadence.
- **isaac-gmail**: unchanged except the cadence.

## Scenarios (worker writes; gchat registrations.feature + inbound.feature)

- first tick registers `spaces/-` for the tenant and nothing else; a configured
  `gchat/spaces` entry does not create a second subscription.
- a message from a space never seen before starts a turn on its canonical
  session, named from one `spaces.get`, tagged with the id.
- a DM's first message starts `gchat-<tenant>-dm-<member>`.
- a later event carrying a new display name renames the session; the tag
  matches, the session id is unchanged.
- renewal inside the window renews the one subscription.

## Acceptance

- Suites green in isaac-gchat and isaac-google; the deleted namespaces have no
  survivors (`grep -rn discover src features spec` empty).
- Yopp config needs no `gchat/spaces` entries for routing; the yopp train
  drops them except where an override is wanted.
- One-time on yopp after deploy: `isaac google status` shows one Chat
  registration per tenant; a DM to the account from a person starts a turn
  without any config change.

## Related

isaac-xy2i (landed 692ce17, parts to remove), isaac-1zkz (tenants),
heartbeat bean (sibling), isaac-mu1i (smoke check `registrations` must adapt).

## Scope note (planner, 2026-09-22)

The tick cadence change (30 s → hourly) is owned by isaac-an14 (heartbeat), which rides the same tick. This bean leaves `default-tick-ms` alone and only removes per-space keys/discovery in gchat.

## Handoff (worker, 2026-09-22)

**isaac-gchat only.** Repo `isaac-gchat`, branch `bean/isaac-ihuc`, one commit
`cd5ba85`, pushed. **isaac-google was not touched** — its pin does not move.

### Deleted (clean cutover, no deprecations)

- `src/isaac/comm/gchat/spaces.clj` + `spec/.../spaces_spec.clj` — the whole
  `spaces.list` listing, its `DEFAULT-EVERY-MS` throttle and `known`.
- `registration/space-keys` (the configured ∪ discovered union).
- `tenant/spaces-for`, `tenant/discovering?`, `tenant/discover-every-ms`,
  `tenant/KIND` — `tenant.clj` is now just `of-comm`.
- Manifest keys `gchat/discover` and `gchat/discover-every-ms`.
- The gate's `:space` drop: an unlisted space no longer fails closed, because
  membership is the grant. `gchat/spaces` entries stay, as overrides only
  (session/session-tags/crew/reach/prefer/create/respond); the manifest
  description now says so.
- Scenarios that only made sense for discovery: the spaces.list tick, the
  "space the account has left" unsubscribe, the throttle scenario, and the
  `:space` half of "unconfigured spaces and unknown senders fail closed".
- `grep -rn discover src features spec` → empty. (`feature-steps` still names
  `isaac.module.discovery`, which is foundation's module loader, not this.)

### The one registration

`registration/subscription-keys` answers `["spaces/-"]` for every organization,
whatever `gchat/spaces` holds. `create!` posts:

```
targetResource        //chat.googleapis.com/spaces/-
eventTypes            message.v1.created / updated / deleted
                      membership.v1.created / updated / deleted
notificationEndpoint  {pubsubTopic <that organization's topic>}
payloadOptions        {includeResource false}
```

`remote-key` in isaac-google's engine parses `spaces/-` straight back out of
the targetResource, so create/renew/delete and the `registrations` smoke check
needed no change. The three membership types and `message.deleted` are wired
to a new `handler/acknowledge-event`, which logs `:gchat/event-noted` at debug
and returns — without it the inbox worker would warn `:google/handler-missing`
and leave every such record pending forever (which `message.deleted` already
did before this bean). A membership deleted for the account does nothing: no
per-space subscription to drop, and the session stays as it is.

### Naming and rename

New `isaac.comm.gchat.lookup`: `space-info` does one `spaces.get` per
`[organization space]` and remembers it; a refusal is logged
(`:gchat.space/unknown`) and *not* remembered, so the next message asks again.
The handler passes the event's own `:space` as a hint — a `displayName` that
differs from the memory replaces it, and that is how a rename is noticed
(there is no space-updated event on this target).

`canon/session-for` became `canon/settle`, returning
`{:session-key k}` or `{:session-key k :rename-from old}`. The tagged session
is always the one; when the canonical name has moved, the handler calls
`session-store/rename-session!` (seam `handler/-rename-session!`) and logs
`:gchat/session-renamed`. A refused rename (turn in flight, name collision)
logs `:gchat/session-rename-failed` and keeps the old name, so a message never
splits the conversation into a second session. Names still always carry the
tenant: `gchat-<tenant>-<slug>` / `gchat-<tenant>-dm-<member>`, tag
`space:<id>`.

**Deliberate deviation:** the bean said "one memoized `spaces.get` (+ members
for a DM)". No `spaces.members.list` was added. The only event a DM ever
produces is a message from the other member, and the gate already names the DM
from that sender's display name — a members call would ask Chat for what the
event already carries. `spaces.get` is still made for a DM (it is what reports
`spaceType DIRECT_MESSAGE` when the event does not).

### Files

isaac-gchat (only repo changed):
- new `src/isaac/comm/gchat/lookup.clj`, `spec/isaac/comm/gchat/lookup_spec.clj`
- deleted `src/isaac/comm/gchat/spaces.clj`, `spec/isaac/comm/gchat/spaces_spec.clj`
- `src/isaac/comm/gchat/{registration,canon,gate,handler,tenant,chat_api}.clj`
  (`chat_api` gains `get-space!`)
- `resources/isaac-manifest.edn`
- `spec/isaac/comm/gchat/{registration,canon,gate,handler,tenant,chat_api}_spec.clj`
- `features/comm/gchat/{registrations,inbound,tenants}.feature`
- `feature-steps/isaac/gchat_steps.clj` — the step
  `the Chat API lists the account's spaces:` is replaced by
  `the Chat API knows space "<name>":`, and the HTTP stub answers `spaces.get`.

### Scenarios

registrations.feature (6): first tick subscribes `spaces/-` and *nothing else*
with two `gchat/spaces` entries configured; the subscription carries messages
and memberships; renew inside the window; left alone outside it; per-space
subscriptions from the old scheme are unsubscribed on the first tick; a
refused create is logged and retried.
inbound.feature: a space nobody configured starts a turn on its canonical
session named from **one** `spaces.get` (two messages, one HTTP call asserted);
an event carrying a new display name renames the session, which keeps its
history and its tag; a DM's first message → `gchat-<tenant>-dm-<member>`; two
spaces sharing a display name get two sessions; an entry's explicit session
still overrides.
tenants.feature: each organization gets its own `spaces/-` subscription, its
own token, its own topic — two POSTs, no more.

### Test commands and counts

```
isaac-gchat:  bb lint src   → 0 errors, 0 warnings
              bb spec       → 123 examples, 0 failures, 223 assertions
              bb features   →  36 examples, 0 failures,  85 assertions
              bb ci         → green (both of the above)
isaac-google: bb spec       → 171 examples, 0 failures, 273 assertions
              bb ci         → green (28 feature examples, 114 assertions)
```

(`bb lint` over `src spec` reports the repo's pre-existing 60-odd
"Unresolved symbol" findings from speclj's `:refer :all`; `bb lint src` is
clean and the new spec files follow the same existing pattern.)

### What needs the live host

- `isaac google status` showing exactly one Chat registration per tenant,
  keyed `spaces/-`, and `isaac google smoke` passing its `registrations` check
  against it.
- That Google actually grants `spaces/-` a 7-day expiry (the docs page did not
  say; the renew window assumes the same 7 days) and that renewal on it works.
- A DM from a person to the account starting a turn with no config at all, and
  a message in a space nobody listed doing the same.
- The yopp/zanebot train should drop `gchat/spaces` entries except where an
  override is actually wanted.

### Note for isaac-an14 (heartbeat)

Health's silence detection is now blind for Chat. `google/http.clj`
`record-health!` records `last-event-at` under the **concrete** space
(`spaces/ENG`), while the only registration key is now `spaces/-`, so
`health/evaluate` finds no `last-event-at` for its key and — because
`silent-hours` returns nil for a key that has never been seen — never raises
`:google/silent` rather than raising it falsely. No suite fails on this; it is
a real gap that belongs with the heartbeat, not here.

## Landed on main

main-sha: isaac-gchat cd5ba85

Planner check 2026-09-22: reran on bean/isaac-ihuc cd5ba85 — `bb spec` 123/0, `bb features` 36/0, no `discover` survivors; registration = one `spaces/-` key per organization with message + membership event types. isaac-google untouched (no pin to move). Fast-forwarded to main; branch deleted. Carried to isaac-an14 (in flight): per-tenant silence must read every last-event-at the health state holds, not the registration keys, or it is blind after this change. Live-host proof still owed: `spaces/-` expiry and renewal, and a DM heard from its first message. Not deployed.
