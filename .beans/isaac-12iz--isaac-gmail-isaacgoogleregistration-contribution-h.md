---
# isaac-12iz
title: 'isaac-gmail: :isaac.google/registration contribution has the wrong shape — the INBOX watch never registers with isaac-google''s timer'
status: completed
type: bug
priority: high
tags:
    - google
    - gmail
created_at: 2026-09-19T19:05:58Z
updated_at: 2026-09-19T19:24:09Z
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



## Handoff / resume (planner, 2026-09-19)
Split: the isaac-google half is **isaac-gxil** (:remote/:delete! hooks; branch bean/isaac-12iz in isaac-google @ ac7a5b3). This bean is the isaac-gmail half: branch bean/isaac-12iz in isaac-gmail @ 82164c1 (base origin/main@21e7241). Done: watch.clj is a real entry (watch!/expiry/keys*/remote/stop!), manifest contributes the six-key entry, features/comm/gmail/watch.feature (first tick watches + seeds cursor; outside window left alone; inside window renewed; mailbox removed → users.stop; refusal logged with Gmail's reason then retried) 5/5, unit spec 8/8, bb ci 23 spec + 10 feature examples green. Waits on gxil landing only because deps/bb pin google at the branch sha; the planner repins, reruns bb ci, tags unverified.



isaac-gxil landed (isaac-google main f9ae6fc). Repinned; cold-cache classpath ok; bb ci 23 spec + 10 feature examples green. branch: bean/isaac-12iz @ 0267f9f (base origin/main@21e7241) in isaac-gmail — fast-forward from main. Version 0.1.3. Handed to verify.



## Landed on main (2026-09-19)

main-sha: isaac-gmail 476e4d22b8dbeb3b0d1817f3482c55cac04614bb
