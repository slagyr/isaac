---
# isaac-jkxd
title: Google Chat replies mirror the room; a Chat-only tool lets the crew choose, once or from now on
status: draft
type: feature
priority: normal
created_at: 2026-10-14T23:22:14Z
updated_at: 2026-10-15T13:33:54Z
---

## Problem

A turn that came from Google Chat always posts its reply into the thread of
the message it answers (`isaac.comm.gchat/reply!` passes `(:thread origin)` to
`post-chunks!`). Every Chat message belongs to a thread, even a plain
top-level DM line, so a person who writes top-level in a DM gets an answer
tucked into a thread under their message. The crew cannot change this: the
reply path never asks it where to post.

People differ. Asked, one user put it as: read the room, mirror what I do,
or do what I tell you.

## Change

1. **A default that mirrors the room.**
   - In a DM: a top-level message gets a top-level reply; a message inside a
     thread (Chat's `threadReply: true`) gets its reply in that thread.
   - In any other space: the reply goes in the thread of the mention
     (today's behaviour).
   - A top-level post omits both `thread` and `messageReplyOption`.
2. **The crew can override it, with a tool granted only on Chat turns.**
   `gchat__reply_in` takes `where` (`thread` | `top-level`) and optional
   `remember` (boolean).
   - Without `remember`: applies to this turn's reply only. Held in memory
     for the turn, keyed by session, cleared once the reply posts.
   - With `remember`: also becomes this space's default for later replies.
     Persisted in a record the module owns, beside the space transcript:
     `<root>/google/chat/<space>.edn`, `{:replies :thread|:top-level}`. Not
     on the agent session record, and not in Isaac config.
   - Precedence: this turn's choice, then the remembered choice, then the
     default.
   - Granted per turn through the system-section berth: the gchat module
     contributes a section with no text that grants `gchat__reply_in` only
     when the turn's session has a Chat turn in flight. No crew grant needed;
     never offered on non-Chat turns. Needs the agent to hand section
     contributors the session key (blocking bean).
3. **Notices follow the reply.** Error, wall and park notices post where the
   reply would.
4. **The guidance says so.** The per-turn Chat guidance
   (`isaac.comm.gchat.guidance`) says whether the message came in top-level
   or in a thread, where the reply will post, and that `gchat__reply_in`
   changes that. The gchat handbook chapter documents the tool and the
   remembered record.

## Scenarios

Drafted in chat; to be committed `@wip` to isaac-gchat
`features/comm/gchat/outbound.feature`, then baselined.

## Acceptance

- `@wip` removed and the new scenarios pass (`bb features` in isaac-gchat).
- The existing two-threads-in-one-DM scenario (isaac-acou) passes with its
  messages marked as thread replies.
- `resources/isaac/gchat/handbook.md` documents `gchat__reply_in`, the
  default rule, and the remembered record's path and shape.
