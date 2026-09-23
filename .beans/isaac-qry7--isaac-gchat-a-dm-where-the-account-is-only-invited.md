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

## Probe results (2026-09-23 03:2xZ, gchat 0.2.4, nine scopes incl. chat.spaces.create)

- spaces:setup as Yopp with Micah as member → 200, returns the existing DM; membershipCount.joinedDirectHumanUserCount stays 1; spaces.get still 403. Setup does NOT join an invited DM.
- members.create for the calling user → 403 insufficient scopes (would need https://www.googleapis.com/auth/chat.memberships, the write scope). Unproven whether it joins a DM; members.get / patch on the own membership → 404 (member name form or not visible while invited).
- Likely cause of the invited state: yopp@ has never opened Google Chat, so its DM memberships are pending until the first Chat sign-in (same organization, so not a message-request policy).

Next: (1) one-time — sign in to Chat as yopp@ once and open the DM; (2) for the future, add chat.memberships (write) to the scope union and try members.create (self) on an invited DM; if that joins, wire it into the inbound path; if not, log :gchat.dm/invited once and deliver via the attention comm. Reply 403s must surface as delivery failures either way.
