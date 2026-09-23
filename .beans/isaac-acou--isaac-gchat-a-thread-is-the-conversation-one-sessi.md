---
# isaac-acou
title: 'isaac-gchat: a thread is the conversation — one session per Chat thread, mention pulls the thread''s history in, replies stay in-thread'
status: todo
type: feature
priority: high
tags:
    - gchat
    - comm
created_at: 2026-09-23T15:51:29Z
updated_at: 2026-09-23T15:51:29Z
---

## Decision (Micah, 2026-09-23)

A Chat thread is a conversation. Today the session is per space (isaac-ihuc), so
every thread in a DM or a room shares one transcript and Yopp's reply to a
thread is informed by every other thread in the space. Threads then offer no
value. Segregate them: the thread is the session, and replying in-thread —
which Yopp already does — becomes exactly right. This supersedes isaac-1nlj
(DM replies as plain messages): a plain DM message opens a new thread, and
that is a new conversation.

## Change (isaac-gchat)

- Session per thread: name `gchat-<tenant>-<space-slug>-<thread-short>` (or the
  room's canonical name + thread short id), tags `space:<id>` and
  `thread:<thread-id>` (verbatim). The space tag keeps the room's identity;
  the thread tag is the match.
- A message with no thread of its own (Chat gives every message a thread
  name) is its thread's first message → new session. Later messages in that
  thread → the same session.
- Mention-only rooms: a mention starts or continues the thread's session, and
  the turn's context is that thread only. If the thread has history before
  the first mention, seed the session with the thread's earlier messages
  (`messages.list` filtered by thread, once) so Yopp knows what it was pulled
  into.
- DMs: `:respond :all` per thread; the reply goes in-thread.
- Rename follows the space; thread sessions carry the space's current name.
- Session listing shows space and thread tags; `gchat-<tenant>-<space>` alone
  (no thread) is no longer created.

## Scenarios (inbound.feature, outbound.feature)

- two threads in one DM → two sessions, each reply in its own thread, neither
  transcript contains the other's messages.
- a mention in a room thread with three earlier messages → the session's first
  request carries those three as context; the reply is in that thread.
- a second mention in the same thread → same session, no re-seed.
- a plain new DM message → a new thread → a new session.

## Acceptance

bb spec / bb features / bb ci green in isaac-gchat; one-time on yopp: two
parallel DM threads with Yopp stay separate.

## Related

isaac-ihuc, isaac-xy2i (canon), isaac-1nlj (superseded), isaac-qry7.
