---
# isaac-q90z
title: Add :history-retention policy with :prune and :retain modes
status: draft
type: feature
priority: normal
created_at: 2026-05-16T17:23:05Z
updated_at: 2026-05-16T19:03:35Z
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

## Why this is state-defining, not behavioral

Unlike `:effort`, `:model`, and `:context-mode` — which are *behavioral parameters* that affect a single turn and can flip freely between turns — `:history-retention` is a *state-defining setting*. Flipping it mid-session creates persistent inconsistency:

- `:retain` → `:prune` mid-stream: the offset pointer points into deleted entries; subsequent compactions further corrupt
- `:prune` → `:retain` mid-stream: pre-change entries are gone forever, post-change entries retained — mixed mode with no clean history walk

So `:history-retention` is **resolved once at session creation and locked onto the sidecar**. The cascade still exists; it just fires at create-time only. Subsequent crew switches, default changes, etc. do **not** flip the retention of an existing session.

This establishes the rule of thumb: *state-defining settings persist on the sidecar at creation; behavioral parameters resolve fresh each turn.* Future state-defining settings (rotation thresholds, etc.) follow the same pattern.

## Config placement

Flat field on each layer (matches `:effort`, `:model`, `:context-mode`):

```clojure
{:defaults {:history-retention :retain}
 :crew     {:main {:history-retention :prune}}      ; optional override
 :models   {:foo  {:history-retention :prune}}      ; rarely set, accepted
 :providers{:bar  {:history-retention :prune}}}     ; rarely set, accepted
```

**Cascade at session creation** (shape mirrors `resolve-effort` in `src/isaac/effort.clj:13`):

```
explicit create-time override > crew > model > provider > :defaults > :retain (hardcoded)
```

First non-nil wins. Resolved exactly once, at session create. Result is written onto the session sidecar.

**Explicit create-time override** is the layer for any session-creator that has its own opinion. Each creator reads its own config and passes the value to `create-session!`. The cascade resolver itself stays oblivious to the diverse set of creators.

Session-creators that may carry `:history-retention` in their config block:
- Hooks (`:hooks {:lettuce {:history-retention :retain ...}}`) — webhook handler reads it and passes through
- Cron jobs (`:cron {:nightly {:history-retention :prune ...}}`)
- ACP / external API callers (programmatic)
- Slash commands like `/new` (user-supplied)

v1 wires this into hook config and the programmatic API. Cron and slash can follow the same pattern when they grow their own needs.

**At every subsequent turn:** the sidecar value is the only thing consulted. Changes to crew/defaults after the session exists do not affect it.

A future slash command (`/retention :prune`) could explicitly mutate the sidecar value, but would need to handle the on-disk state transition (e.g., a `:prune → :retain` flip is harmless going forward; `:retain → :prune` would need to either prune now or leave residue). Out of scope for v1; v1 is immutable post-creation.

## Scope

- `splice-compaction!` in both `file_impl.clj` and `index_impl.clj` learn the retention branch
- Session sidecar gains `:effective-history-offset` field; updated atomically with compaction write
- Prompt-build read path uses the offset
- Session-creation path resolves the cascade and writes `:history-retention` onto the sidecar (one-shot; immutable post-creation in v1)
- Existing compaction tests flip their assertions: under `:retain`, originals are present on disk but absent from the LLM view
- Backup-transcript dance (`backup-transcript!`) becomes irrelevant under `:retain` — separate cleanup

## Out of scope

- Transcript rotation / compression (separate bean, blocked by this)
- Slash command for runtime override (can land later)
- Migrating existing sessions (new policy applies to new sessions; old ones keep their resolved value)

## Sibling

`isaac-cdqk` (`:context-mode` :full/:reset) is in the same neighborhood but a *behavioral parameter*, not state-defining. It resolves fresh on every turn from the live crew config (`src/isaac/drive/turn.clj:653`) and can flip freely between turns. Its cascade is currently thinner (only crew → hardcoded `:full`).

The pattern split:

| Setting              | Kind             | Resolution     | Lives on sidecar?     |
|----------------------|------------------|----------------|-----------------------|
| `:effort`            | behavioral       | every turn     | only if user-set      |
| `:model`             | behavioral       | every turn     | only if user-set      |
| `:context-mode`      | behavioral       | every turn     | no                    |
| `:history-retention` | **state-defining** | **once at create** | **always**       |
| `:effective-history-offset` | state-data | written by splice | always (data, not config) |

## Feature file

`features/session/history_retention.feature` — four @wip scenarios:

1. Under :retain, compacted entries remain in the transcript file
2. Under :prune, compacted entries are removed from the transcript file (regression)
3. Retention is locked at session creation; changing defaults later does not flip it
4. Explicit create-time override wins over crew and defaults

## Acceptance

```
bb features features/session/history_retention.feature
```

All four scenarios pass; remove `@wip`.
