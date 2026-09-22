---
# isaac-ihuc
title: 'One Chat subscription per tenant on spaces/-: delete per-space registration, discovery and the 30 s tick; naming by memoized spaces.get; rename follows'
status: todo
type: feature
priority: high
tags:
    - google
    - gchat
    - comm
created_at: 2026-09-22T23:26:20Z
updated_at: 2026-09-22T23:26:20Z
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
