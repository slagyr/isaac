---
# isaac-810d
title: 'isaac-gchat: human senders never pass allow-from — Chat returns users/<id> + domainId, not an email'
status: completed
type: bug
priority: critical
tags:
    - google
    - comm
created_at: 2026-09-19T23:42:01Z
updated_at: 2026-09-19T23:42:33Z
parent: isaac-bv1l
---

Found 2026-09-19 23:37Z on yopp, first inbound Chat message through the door: dropped `:sender`. spaces.messages.get under user auth returns `{:sender {:name "users/118…" :displayName "Micah Martin" :type "HUMAN" :domainId "0ivzlyj"}}` — no email — so an email allow-list can never admit a person; every fixture assumed sender.email.

Fix: allow-from entries match by email (when present), by `users/<id>`, or by `domain:<domainId>` (the Workspace customer id, C00ivzlyj on tonotop.com, as Chat reports it); a :sender drop logs the identity at :info so the operator can copy it in; schema description says so. Specs + inbound.feature scenarios (users/<id> admitted; domain admitted/refused; drop names the sender). Version 0.1.3. isaac-xy2i (patterns) still stands for *@domain sugar; this makes the list usable at all.

## Acceptance
    cd isaac-gchat && bb ci   # 42 spec / 16 feature green

## Handoff / resume
branch: bean/gchat-sender-identity @ 49c58a0 (base origin/main@a769d6b). Planner lands + deploys (zanebot out of tokens).



## Landed on main (2026-09-19)
main-sha: isaac-gchat 3e993df2dba05fe189f91df42e878e5626893598
Verified by the planner at Micah's instruction (zanebot out of provider tokens). Registry pinned; deployed to yopp.
