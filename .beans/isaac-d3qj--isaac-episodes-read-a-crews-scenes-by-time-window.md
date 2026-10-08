---
# isaac-d3qj
title: 'isaac-episodes: read a crew''s scenes by time window'
status: draft
type: feature
created_at: 2026-10-08T20:41:16Z
updated_at: 2026-10-08T20:41:16Z
parent: isaac-pcm3
blocking:
    - isaac-b1ir
---

DRAFT. Needs scenarios before it is todo. Part of the prompts-and-habits epic; dreaming (isaac-b1ir) needs it.

## Problem

A crew cannot read its own history by time. `recall__search` takes only a query and returns at most eight gists; `recall__scene` needs a scene id. There is no tool and no CLI flag for "everything since Tuesday". A dream has to read all activity since the last dream, across every session of the crew.

The Clojure API already has the data: `store/list-episodes` returns a crew's episodes across sessions sorted by timestamp id, and scene frontmatter carries `started-at` and `ended-at`.

## Proposal

- A crew tool, working name `recall__window`: `since`, optional `until`, optional paging. Returns the calling crew's sealed scenes in time order: scene id, session, times and gist. The body is fetched with the existing `recall__scene`.
- `isaac episodes list --since/--until` for operators.

## To settle

- **Attribution.** A scene is distilled text. If the distillation drops who said what, a dream cannot weigh a lesson by its speaker. Either scene frontmatter records the participants (the `:from` references of the turns it covers, once isaac-agent attribution lands), or the dream falls back to the raw transcript span. Check what the gist model keeps today before choosing.
- Routine scenes (gist prefixed `~`) are skipped by the index. Whether a window includes them.
- Unsealed tails: a conversation still open when the dream runs. Skip and pick up next time, by watermark on `ended-at`.

## Likely repo scope

`isaac-episodes`.
