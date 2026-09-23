---
# isaac-acou
title: 'isaac-gchat: thread-aware replies inside the space session — every message carries its thread, and the comm''s guidance tells Yopp to build context from the thread it is answering'
status: in-progress
type: feature
priority: high
tags:
    - gchat
    - comm
created_at: 2026-09-23T15:51:29Z
updated_at: 2026-09-23T16:17:12Z
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

## Revised (Micah, 2026-09-23): keep the session per space

"We do not want to create a new session for each thread. Keep the session-per-space mapping. But it would be good to track which messages belong to which threads. The gchat comm should add extra system prompt to tell Yopp that it needs to group messages by thread to build thread context before replying."

The session-per-thread design above is withdrawn. What stands:

- **Session per space** (isaac-ihuc canon), unchanged. One transcript holds every thread in the space, which is what lets Yopp hear a whole room while speaking only when spoken to (isaac-iv5c).
- **Every inbound message names its thread.** The transcript entry the gchat comm writes for a Chat message carries the thread id in a stable, visible form — e.g. a leading marker `[thread:mtEwy7PEiSs]` before `Micah Martin: …` in the rendered user text, and `:thread` on the entry metadata. Yopp's own replies carry the same marker for the thread they went to. Thread ids are opaque; a short stable form (last 8–10 chars) is fine as long as it is unique within the space.
- **Comm guidance.** gchat passes `:guidance` on the charge (the seam hail uses for its metadata preamble) with a short standing instruction: messages are grouped by thread markers; the message that addressed you names the thread you are answering; build your context from that thread first — other threads in this space are separate conversations, use them only if the current thread refers to them; reply in the addressed thread. Wording lives in one place (a gchat namespace), not in the crew soul.
- **Reply target** unchanged: in-thread for the message that triggered the turn. A plain new DM message opens a new thread and Yopp replies in it — that is the correct shape, so isaac-1nlj stays scrapped.
- **Mention-only rooms**: because the comm already hears every message in a joined space, the thread's earlier messages are already in the transcript when a mention arrives; no seeding needed. A message that arrived before the space was joined is simply absent (acceptable).

## Scenarios (inbound.feature / outbound.feature, Marigold)

- two threads in one DM, interleaved; Yopp is addressed in thread B → the request's system text carries the thread guidance; both threads' messages are in the transcript, each with its marker; the reply goes to thread B.
- a room with a mention in thread A after three earlier A messages and two B messages → transcript entries carry the right markers; reply in A.
- Yopp's reply entry carries the marker of the thread it went to.
- the guidance text is present exactly once in the system prompt of a gchat-originated turn and absent from a non-gchat turn.

## Acceptance

bb spec / bb features / bb ci green in isaac-gchat; one-time on yopp: two DM threads answered in the right threads with answers that stay on their own topic.
