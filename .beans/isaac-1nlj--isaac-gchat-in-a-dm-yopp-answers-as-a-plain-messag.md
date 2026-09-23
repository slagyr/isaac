---
# isaac-1nlj
title: 'isaac-gchat: in a DM, Yopp answers as a plain message, not a thread reply — threads are for rooms'
status: todo
type: bug
priority: normal
tags:
    - gchat
created_at: 2026-09-23T15:10:20Z
updated_at: 2026-09-23T15:10:20Z
---

## Observed (yopp, 2026-09-23, gchat 0.2.5)

Micah's first delivered DM answer arrived as a threaded reply to his message. In a room that is the right shape (reply in the thread you were addressed in). In a DM people send plain messages; a thread reply reads as odd and collapses the conversation into a side thread.

## Change

When the space is a DIRECT_MESSAGE (space-info :spaceType, per isaac-f4ab's lookup), the reply posts without a :thread (top-level message in the DM). Rooms keep the in-thread reply. Splitting into chunks keeps working in both.

## Scenarios (outbound.feature)

- a mention in a room → the reply carries the thread of the message it answers (existing behaviour, pin it).
- a DM → the reply carries no thread.

## Acceptance

bb spec / bb features / bb ci green in isaac-gchat; one-time on yopp: a DM answer appears as a normal message.
