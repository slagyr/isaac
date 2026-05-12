---
# isaac-nt7a
title: "Failed turns leave orphan user messages with no error breadcrumb"
status: completed
type: bug
priority: normal
created_at: 2026-04-28T15:48:06Z
updated_at: 2026-04-28T19:06:31Z
---

## Description

When run-turn! throws an uncaught exception (e.g. the
ClassCastException from isaac-kqis), the transcript is left in a
lopsided state and the next turn's LLM sees no record of the failure.

## Sequence

1. run-turn! (src/isaac/drive/turn.clj) appends the user message
   before calling the LLM.
2. Crash happens later (post-LLM, pre-execute, in a notification, etc.).
3. acp/server.clj catches and emits a text-update notification to
   the client only — nothing goes to disk.
4. Transcript JSONL now ends:
       ... assistant
       ... user        <- the message that triggered the failure
   with no closing assistant entry.
5. Next user prompt arrives. LLM is handed a transcript ending
   user -> user, no breadcrumb of what went wrong. It tries to
   re-orient (Marvin observed running bd prime + re-reading AGENTS.md).

## Spec

features/session/error_handling.feature has a new @wip scenario:
"uncaught exception during a turn lands a closing error entry".

## Implementation

- run-turn!'s catch (src/isaac/drive/turn.clj around lines 542-549):
  on uncaught Exception, persist an :error transcript entry via
  storage/append-error! before re-throwing. The entry should:
    - record the exception (:content = message, :ex-class = class)
    - structurally close the trailing user message so the next turn
      sees a balanced user/assistant alternation.
- Emit a new structured log event :session/turn-failed with the
  error class + message + session id (used in the spec assertion).

## New step

  the LLM throws an exception with message "X"

This step needs to install a redef of the chat dispatch path that
throws the named exception on next call. One new step total; rest
of the scenario uses existing infrastructure (`the user sends "X"
on session "Y"`, transcript matching, log assertions).

## Definition of done

- features/session/error_handling.feature scenario passes without @wip
- A spec also covers the cli/prompt path so the orphan-user issue is
  fixed there too (single-source the storage/append-error! call inside
  run-turn!'s catch, so all callers benefit)
- bb features and bb spec green

## Related

- isaac-kqis (oauth-device tool args) is one trigger that
  surfaced this. There will be others — this is a generic
  resilience gap, not a fix-the-trigger bead.

## Notes

Verification failed: the ACP path now double-appends error breadcrumbs. Reproduced in-memory by forcing an exception inside isaac.drive.turn/check-compaction! during acp/server dispatch: the session transcript ended with two consecutive error entries from the same exception. drive/turn appends an error entry in its uncaught-exception catch and rethrows, then src/isaac/acp/server.clj catch at lines 149-157 appends the same error again. That means the failure breadcrumb is duplicated, and the next-turn transcript hand-off is not a clean balanced user/assistant close. Full bb spec and bb features are green, and prompt/builder now includes error entries for the LLM, but this ACP duplicate-persistence regression means the bead does not yet fully meet the acceptance criteria.

