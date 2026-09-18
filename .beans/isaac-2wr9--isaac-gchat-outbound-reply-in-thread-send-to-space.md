---
# isaac-2wr9
title: 'isaac-gchat outbound: reply in thread; send to space, DM by email, group DM'
status: draft
type: feature
priority: high
tags:
    - google
    - comm
created_at: 2026-09-18T04:12:15Z
updated_at: 2026-09-18T04:12:15Z
parent: isaac-bv1l
blocked_by:
    - isaac-0gtc
---

Isaac speaks in Google Chat: reply in the thread a turn came from, and send unprompted to a space, a DM, or a group DM.

## Scope (isaac-gchat)

- Comm reply path: turn replies go to the origin space + thread (`spaces.messages.create` with `thread.name`, `messageReplyOption` so a new thread is not opened).
- Outbound send (`send-schema`, as iMessage declares): `:gchat/space` (spaces/…), or `:gchat/to` a user email → `spaces.findDirectMessage`, create with `spaces.setup` when absent; `:gchat/thread` optional. Group DMs are spaces to the API — no special case.
- Message cap + chunking like Discord (`:gchat/message-cap`, Chat's limit is 4096 chars); markdown → Chat formatting is a small translate step (bold/italic/code/links); tables degrade to code blocks.
- Sent as the Google user (same OAuth token). The echo drop from child 3 must ignore what this bean sends.

## Scenarios to draft (stubbed Chat API)

1. A turn's reply is posted in the originating thread.
2. `comm send` to a user email that has no DM space creates one, then posts.
3. A reply above the cap is split into ordered chunks, capped at N.
4. What Isaac sends does not come back as a new turn (with child 3's gate).
