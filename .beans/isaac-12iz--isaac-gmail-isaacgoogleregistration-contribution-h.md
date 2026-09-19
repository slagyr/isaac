---
# isaac-12iz
title: 'isaac-gmail: :isaac.google/registration contribution has the wrong shape — the INBOX watch never registers with isaac-google''s timer'
status: todo
type: bug
priority: high
tags:
    - google
    - gmail
created_at: 2026-09-19T19:05:58Z
updated_at: 2026-09-19T19:05:58Z
parent: isaac-bv1l
---

Found 2026-09-19 installing isaac.comm.gmail on yopp. `isaac config validate`:
`module-index["isaac.comm.gmail"].isaac.google/registration[:gmail-watch] - can't coerce isaac.comm.gmail.watch/registration-entry to map`.

isaac-google's berth wants `{:create! <sym> :renew! <sym> :expiry <sym> :key <sym>}` per entry (see isaac-gchat's `:chat` entry and `isaac.google.registration/register!`, which resolves those four symbols). isaac-gmail contributes `{:gmail-watch isaac.comm.gmail.watch/registration-entry}` — a single symbol whose fn returns a watch descriptor `{:type :gmail/watch :label ... :url ... :body ...}`. `register!` would `(update sym :create! ...)` on a symbol. So the Gmail watch (users.watch on INBOX → Pub/Sub topic, renewed weekly) is not wired into the shared registration/renewal timer at all; gmail's own harness must be driving the watch some other way, or not at all.

Do: contribute `{:gmail-watch {:create! isaac.comm.gmail.watch/create! :renew! isaac.comm.gmail.watch/renew! :expiry isaac.comm.gmail.watch/expiry :key isaac.comm.gmail.watch/keys}}` — create!/renew! call users.watch with the topic from google.topic, expiry from the watch response's expiration, key = the account (one watch per mailbox) — and make features/… prove it through `the google registration timer ticks` like gchat's registrations.feature does. Until this lands isaac.comm.gmail is NOT installed on yopp (removed from its pins 2026-09-19); registry entry stays.

## Scenarios (@wip, worker writes — isaac-gmail features)
1. the first registration tick calls users.watch for the configured account with the shared topic and INBOX label, and `isaac google status` lists the watch with its expiry
2. a watch within renew-within-hours of expiry is renewed on the next tick
3. `isaac config validate` with isaac-gmail installed reports no contribution error (one-time acceptance check, not a permanent scenario)

## Acceptance
    cd isaac-gmail && bb ci; then install on yopp and `isaac config validate` clean of gmail errors.
