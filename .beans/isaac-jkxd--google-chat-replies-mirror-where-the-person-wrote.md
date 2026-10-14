---
# isaac-jkxd
title: Google Chat replies mirror where the person wrote, with a per-space :replies override
status: draft
type: feature
created_at: 2026-10-14T23:22:14Z
updated_at: 2026-10-14T23:22:14Z
---

## Problem

A turn that came from Google Chat always posts its reply into the thread of
the message it answers (`isaac.comm.gchat/reply!` passes `(:thread origin)` to
`post-chunks!`). Every Chat message belongs to a thread, even a plain
top-level DM line, so a person who writes top-level in a DM gets an answer
tucked into a thread under their message. The crew cannot change this: the
reply path never asks it where to post.

People differ. Some want threads, some want a flat DM. Asked, one user put
it as: mirror what I do, unless I tell you otherwise.

## Change

1. **A `:replies` option per space** in `:gchat/spaces` entries:
   `:match`, `:thread` or `:top-level`. A DM is a space with one person, so a
   DM entry is a per-person setting.
   - `:match` — reply where the person wrote: a top-level message gets a
     top-level reply; a message inside a thread (Chat's `threadReply: true`)
     gets its reply in that thread.
   - `:thread` — always reply in the message's thread (today's behaviour).
   - `:top-level` — always reply top-level, no thread.
   - Default: `:match` in a DM, `:thread` in any other space (group spaces
     keep today's behaviour).
2. **Notices follow the reply.** Error, wall and park notices post where the
   reply would.
3. **The crew can change it.** The per-turn Chat guidance
   (`isaac.comm.gchat.guidance`) names the space the message came from and
   where the reply will post, so a crew granted `handbook__configure` can set
   `comms.gchat.gchat/spaces.<space>.replies` when a person asks. The gchat
   handbook chapter documents the option and that path.

Top-level posts omit both `thread` and `messageReplyOption`.

## Scenarios

Drafted in chat; to be committed `@wip` to isaac-gchat
`features/comm/gchat/outbound.feature`, then baselined.

## Acceptance

- `@wip` removed and the new scenarios pass (`bb features` in isaac-gchat).
- The existing two-threads-in-one-DM scenario (isaac-acou) passes with its
  messages marked as thread replies.
- `:replies` is in the manifest schema with the three values, and documented
  in `resources/isaac/gchat/handbook.md`.
