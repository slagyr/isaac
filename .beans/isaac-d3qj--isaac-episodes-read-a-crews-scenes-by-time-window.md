---
# isaac-d3qj
title: 'isaac-agent: a crew reads its conversations by time window'
status: todo
type: feature
priority: normal
created_at: 2026-10-08T20:41:16Z
updated_at: 2026-10-08T21:40:44Z
parent: isaac-pcm3
blocking:
    - isaac-b1ir
---

Part of the prompts-and-habits epic; dreaming (isaac-b1ir) needs it. Planned with Micah 2026-10-08.

## Problem

A crew cannot read its own history by time. There is no tool and no CLI flag for "everything since Tuesday", in either context mode. Today a crew needs file access inside the Isaac root to see its own past conversations. A dream has to read all activity since the last dream, across every session of the crew.

## Why isaac-agent, once

The transcript is the same thing in both context modes. Every session, chronicle or episodes, has one append-only EDNL transcript in the session store; each entry has a timestamp and a role. The store SPI already exposes the full record (`chronicle-transcript`) and a crew's sessions (`list-sessions-by-agent`). An episode's scenes are spans over that same transcript plus a gist, and scene text drops the roles. So a reader over transcripts serves both kinds of crew and keeps who said each line. Nothing in `isaac-episodes` changes.

## What to build

Two crew tools (names ruled by Micah: the `session__` family):

- `session__list` — `since`, optional `until`. The calling crew's sessions that have messages in the window, one line each: name, message count in the window, first..last message time in the window.
- `session__read` — `session`, `since`, optional `until`, optional `offset`. That session's messages in the window as labelled lines: `<iso time> <role>: <text>`. Tool results are dropped; tool calls render as short markers.

And for operators: `isaac sessions list --since <iso> [--until <iso>]`, filtering on the session's updated-at.

## Rules

- **Crew-scoped.** Another crew's session is never listed. Reading one gives the same error as a session that does not exist, so names cannot be probed.
- **Not granted by default.** A crew gets these only when its config allows them. Reading across all of a crew's sessions can carry one person's DM into another conversation.
- **Pages at the tool output cap.** No `limit` parameter. `session__read` fills its page up to the configured `defaults.tools.max-lines` / `max-bytes`, ends on a whole message, and prints `<n> of <total> messages; next offset <n>`. It must not be chopped by the generic cap (no "truncated" marker). A single message larger than the cap still takes the generic truncation.
- **Full record.** Read the full transcript record, including retained segments, not only the live window after compaction.

## Notes for the implementer

- The scenarios need a `timestamp` column on the existing `session "..." has transcript:` step. The step ignores time today; seeded entries get the wall clock.
- The message renderer (drop tool results, summarize tool calls) exists in `isaac.session.episodes.distill` in `isaac-episodes`. The agent cannot depend on that module; write the agent's own small renderer. Moving the episodes one down is a later cleanup, not this bean.
- With `history-retention :prune`, compaction drops the old prefix and it cannot be read. Document it; do not work around it.
- Later, not this bean: each line carries the turn's `:from` once attribution (isaac-v403) lands.

## Likely repo scope

`isaac-agent`.

## Acceptance

Run from `isaac-agent`, with `@wip` removed from the feature file and from the CLI scenario:

- `bb features features/tool/session_history.feature`
- `bb features features/session/cli.feature`
- `bb verify` and `bb jvm-spec` green.
- The agent handbook chapter (`resources/isaac/agent/handbook.md`) documents `session__list` and `session__read`: what they return, that they are crew-scoped, that they are not granted by default and how to allow them, the paging line, and the `:prune` limit. It says plainly that a crew which cannot see `session__list` is missing the grant, so a model asked to review its history can tell the operator why it cannot.
- `isaac sessions --help` shows `--since` and `--until`.

feature-baseline: isaac-agent 2bcf03b12ec315bdfff6df4d532057dd7c193d24
feature-blob: isaac-agent features/tool/session_history.feature 7da784ae7a6dd95d7195a130df8efe5885ccb0c5
feature-blob: isaac-agent features/session/cli.feature 857fed1619c416a3215203dec61b716a18848fe2 437
