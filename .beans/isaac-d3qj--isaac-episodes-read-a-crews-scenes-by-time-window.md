---
# isaac-d3qj
title: 'isaac-agent: a crew reads its conversations by time window'
status: draft
type: feature
priority: normal
created_at: 2026-10-08T20:41:16Z
updated_at: 2026-10-08T21:19:22Z
parent: isaac-pcm3
blocking:
    - isaac-b1ir
---

DRAFT. Needs scenarios before it is todo. Part of the prompts-and-habits epic; dreaming (isaac-b1ir) needs it.

Re-scoped 2026-10-08 from `isaac-episodes` to `isaac-agent` after Micah's question: chronicle crews must be dreamable too, and episodes add nothing but gists.

## Problem

A crew cannot read its own history by time. There is no tool and no CLI flag for "everything since Tuesday", in either context mode. A dream has to read all activity since the last dream, across every session of the crew.

## Why isaac-agent, once

The transcript is the same thing in both modes. Every session, chronicle or episodes, has one append-only EDNL transcript in the session store; each entry has a timestamp and a role. The store SPI already exposes the full record (`chronicle-transcript`) and a crew's sessions (`list-sessions-by-agent`). An episode's scenes are spans over that same transcript (`start-id`..`end-id`) plus a gist; scene text is the message texts joined verbatim, with the roles thrown away (`isaac.session.episodes.segment/seal-scenes`, checked on isaac-episodes beedc19).

So a reader over transcripts serves both kinds of crew, and it keeps what scenes lose: who said each line. Nothing in `isaac-episodes` has to change for dreaming.

## Proposal

Two crew tools, working names:

- list the calling crew's sessions that have activity in a window (`since`, optional `until`): session id, origin, first and last message time, message count;
- read one session's messages inside the window, paged, as labelled lines: time, role, and the turn's `:from` once attribution (isaac-v403) lands. Tool results are dropped and tool calls shown as short markers, as the episodes distiller does today.

Plus `isaac sessions list --since/--until` for operators.

## To settle

- **Size.** A day of raw conversation is far larger than a day of gists. Paging and a per-call budget are required; the two-step shape lets the dream skip sessions that are plainly routine.
- **The message renderer.** Dropping tool results and summarizing tool calls is `isaac.session.episodes.distill` today. Either the agent grows the same rendering, or that function moves down into the agent and episodes calls it.
- **Pruned history.** With `history-retention :prune`, compaction drops the old prefix and it cannot be dreamed over. `:retain` is the default. Say so in the handbook; do not work around it.
- **Open conversations.** The watermark is a message timestamp, so a conversation still in progress is read up to now and picked up from there next time.
- **Later, optional:** when the crew runs episodes, gists could serve as a cheap table of contents for the listing step. Not needed to start.

## Likely repo scope

`isaac-agent`.
