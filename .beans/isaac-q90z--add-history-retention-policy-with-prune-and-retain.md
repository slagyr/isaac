---
# isaac-q90z
title: Add :history-retention policy with :prune and :retain modes
status: draft
type: feature
created_at: 2026-05-16T17:23:05Z
updated_at: 2026-05-16T17:23:05Z
---

## Problem

Today `splice-compaction!` (`src/isaac/session/store/file_impl.clj:560` and the parallel `index_impl.clj`) **physically removes** compacted entries from the transcript file. The on-disk transcript stays in sync with the LLM's view, but at the cost of losing the original history. No replay, no forensics, no audit trail past the most recent summary.

The thinking behind staying lossy was "transcripts grow forever, disk is precious." That tradeoff has flipped: disk is cheap, log rotation is well-established, and the pre-compaction record has real value (debugging, accountability, future analytics).

## Approach

Introduce a `:history-retention` field with two values:
- `:prune` — current behavior (delete compacted entries on splice)
- `:retain` — keep compacted entries on disk; insert the compaction marker in place; record the marker's byte offset on the session sidecar

Default: `:retain`.

Prompt-build read path:
- When the session has an `:effective-history-offset`, seek to that offset and read forward. Pre-compaction entries are never read on the hot path.
- When no offset (no compactions yet, or `:prune` mode), read the whole transcript as today.

`messages-after-compaction` / `messages-from-entry-id` in `src/isaac/session/compaction.clj` are already pointer-based and naturally skip pre-compaction entries — no logic changes needed, just don't read them from disk in the first place.

## Config placement

```clojure
{:defaults {:history-retention :retain}
 :crew {:main {:history-retention :prune}}}  ; optional override
```

Cascade (sibling pattern to `:context-mode` from isaac-cdqk):
1. Installation defaults (`:defaults`)
2. Crew config (overrides installation)
3. Resolved-and-persisted onto the session at creation (so later default changes don't retroactively flip live sessions)

Explicit per-session override available via slash command later.

## Scope

- `splice-compaction!` in both `file_impl.clj` and `index_impl.clj` learn the retention branch
- Session sidecar gains `:effective-history-offset` field; updated atomically with compaction write
- Prompt-build read path uses the offset
- Session resolution writes `:history-retention` onto the sidecar at creation
- Existing compaction tests flip their assertions: under `:retain`, originals are present on disk but absent from the LLM view
- Backup-transcript dance (`backup-transcript!`) becomes irrelevant under `:retain` — separate cleanup

## Out of scope

- Transcript rotation / compression (separate bean, blocked by this)
- Slash command for runtime override (can land later)
- Migrating existing sessions (new policy applies to new sessions; old ones keep their resolved value)

## Sibling

`isaac-cdqk` (`:context-mode` :full/:reset) uses the same cascade pattern but answers a different question. Both fields live on the session sidecar; both resolve from defaults → crew → session at session creation.

## Feature file

`features/session/history_retention.feature` (scenarios deferred; draft this bean for prosperity, flesh out later)
