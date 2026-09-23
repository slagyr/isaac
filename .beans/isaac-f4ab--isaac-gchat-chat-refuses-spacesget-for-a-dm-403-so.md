---
# isaac-f4ab
title: 'isaac-gchat: Chat refuses spaces.get for a DM (403) so the lookup never learns it is a DM and no turn starts — fall back to spaces.list'
status: in-progress
type: bug
priority: high
tags:
    - gchat
    - google
created_at: 2026-09-23T02:19:53Z
updated_at: 2026-09-23T02:19:53Z
---

## Observed (yopp, 2026-09-23 02:16Z, gchat 0.2.2, one spaces/- subscription)

Micah's first DM arrived through spaces/- (google/push-received). The handler asked spaces.get for spaces/26gscqAAAAE and Chat answered 403 PERMISSION_DENIED ('or the resource does not exist'), logged as :gchat.space/unknown. The same token gets 200 from spaces.get on the room, from messages.list on that DM, and from spaces.list, whose entry for the DM says spaceType DIRECT_MESSAGE. The fetched message's :space carries only the name. With space-info nil, dm? is false, the respond policy becomes :mentions, and the DM is only logged (gchat/message-logged) — no turn.

## Change

lookup/ask!: when spaces.get fails, list the account's spaces (chat_api/list-spaces!, one paged call, memoized like the get) and take the entry whose :name matches; that entry carries :spaceType and, for rooms, :displayName. A DM's name comes from the other member, i.e. the sender of the message that revealed it (already how canon names DMs). If neither call answers, keep today's nil.

## Scenarios (inbound.feature)

- spaces.get 403 for the DM, spaces.list names it DIRECT_MESSAGE → the DM starts a turn on gchat-<tenant>-dm-<sender>.
- spaces.get 200 → no list call.

## Acceptance

bb spec / bb features / bb ci green in isaac-gchat; one-time on yopp: a DM starts a turn.
