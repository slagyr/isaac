---
# isaac-hxrp
title: "Compaction recovery: chunked summaries, cross-turn surrender, reset on model change"
status: completed
type: feature
priority: high
created_at: 2026-04-28T18:47:11Z
updated_at: 2026-04-29T03:35:21Z
---

## Description

Spec: features/context/compaction.feature (three @wip scenarios at the
end). This is the recovery half of the work that isaac-karq deferred.

Live failure on zanebot's clever-signal session: compaction failed every
turn for 45+ minutes, transcript climbed past 1.3M tokens against a
278k window. Cause: the compaction request itself exceeds the context
window (the head we want to summarize is bigger than the model can
read in one shot).

## Scenarios to make pass

1. Chunked compaction (@wip).
   When the compactable head exceeds context-window in a single
   summarization call, split into chunks, summarize each, then
   summarize the summaries. Emit :session/compaction-chunked.

2. Cross-turn surrender (@wip).
   Track :compaction.consecutive-failures on the session record across
   turns. After max-compaction-attempts (5) consecutive failures, set
   :compaction-disabled true on the session and stop attempting until
   reset. Emit :session/compaction-stopped with :reason
   :too-many-failures.

3. Reset on model change (@wip).
   When session_model mutates a session's :model, clear
   :compaction-disabled and zero out :compaction.consecutive-failures.
   The natural signal that recovery conditions might have changed.

## Implementation notes

src/isaac/drive/turn.clj
  - perform-compaction! (line 312): on :error result, increment
    consecutive-failures on the session record before logging
  - on success, zero consecutive-failures
  - run-compaction-check! (called from check-compaction!): early-exit
    when :compaction-disabled is true on the session
  - emit log entry :session/compaction-stopped with reason when the
    threshold is hit

src/isaac/context/manager.clj
  - compact!: if the compactable slice exceeds context-window, split
    into chunks of <= context-window/2 each, summarize each via the
    chat-fn, then summarize the joined summaries. Emit
    :session/compaction-chunked once per chunked compaction (not per
    chunk). The final summary becomes the compaction entry's :summary.

src/isaac/tool/builtin.clj
  - session_model handler (lines around 587-621): when persisting a
    new :model, also assoc :compaction-disabled false and
    :compaction.consecutive-failures 0.

src/isaac/session/storage.clj
  - May need to extend session-entry-keys to include
    :compaction-disabled and :compaction.consecutive-failures so they
    round-trip through reads/writes.

## Depends on

- isaac-sqjf (session_model implementation) — scenario 3 calls the
  session_model tool. Land sqjf first.

## Definition of done

- All three scenarios pass without @wip
- Existing 'Compaction failure is logged and chat proceeds without
  looping' scenario still passes
- bb features and bb spec green
- A live test on zanebot's clever-signal: replace the session,
  confirm one turn either succeeds via chunking or surrenders cleanly
  with compaction-disabled

## Out of scope

- Manual /compact-reset slash command (would also clear the disabled
  flag). Possible future bead. Current escape is start a new session.

## Notes

Verified current main with both automated checks and live mounted-session evidence. Local checks: bb spec and bb features are green; features/context/compaction.feature passes with all recovery scenarios active. Live evidence from /Volumes/Macintosh HD-1/Users/zane/.isaac/sessions shows clever-signal has :compaction-count 2, :compaction-disabled false, and transcript entries 0861ba95 and 6d13ace1 are compaction summaries, reducing the reported context from ~1,023,871 over 278,528 down to 102,394 after the first compaction and 554 after the second. No new code changes were needed in this session.

