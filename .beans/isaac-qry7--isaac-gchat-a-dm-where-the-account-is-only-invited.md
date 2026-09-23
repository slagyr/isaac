---
# isaac-qry7
title: 'isaac-gchat: a DM where the account is only invited (message request pending) gets a 403 on reply — join it, and request chat.spaces.create for Yopp-initiated DMs'
status: todo
type: bug
priority: high
tags:
    - gchat
    - google
created_at: 2026-09-23T03:02:50Z
updated_at: 2026-09-23T03:02:50Z
---

## Observed (yopp, 2026-09-23 02:59Z, gchat 0.2.3)

Micah's DM arrived via spaces/-, the lookup fell back to spaces.list (isaac-f4ab), the turn ran on gchat-tonotop-dm-micah-martin and the model answered — then the reply's messages.create returned 403 and the turn ended :error. Probes with the same token: spaces.get, members.list and messages.create on the DM all 403 'Permission denied … or the resource doesn't exist'; messages.list and spaces.list work; findDirectMessage returns the DM with membershipCount.joinedDirectHumanUserCount = 1. Yopp's side of the DM is a pending message request (the account has never opened Chat), and an invited member receives events but cannot read or post. spaces:setup as Yopp answered 'insufficient authentication scopes': the login never requested chat.spaces.create, so gchat.clj's outbound find-direct-message → setup-direct-message path has never been able to run either.

## Change

1. Login scope: add https://www.googleapis.com/auth/chat.spaces.create to gchat's :isaac.google/scopes contribution (needed for spaces:setup, i.e. Yopp-initiated DMs via comm__send to a person).
2. Inbound DM the account is not joined to: detect it (findDirectMessage's joinedDirectHumanUserCount < 2, or the first 403 on the space) and try spaces:setup with the other member; if Google joins the account that way, proceed; if it does not, log :gchat.dm/invited once with the space uri and the operator action (open Chat as the account and accept), and deliver the reply through the fallback comm (attention) instead of ending the turn :error.
3. A reply that fails 403 is reported as a delivery failure with the space and the reason, not a bare 'Chat API create failed: 403'.

## Verify live

Whether spaces:setup joins an already-invited DM is unknown — probe on yopp once the scope is granted; the outcome decides whether step 2 is 'join' or 'log and hand to the operator'.

## Related

isaac-f4ab, isaac-ihuc, the yopp rollout record (engineering/yopp/google-rollout.md, 2026-09-23).
