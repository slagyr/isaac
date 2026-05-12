---
# isaac-upve
title: "Sessions command UX: format list, add show, add delete"
status: todo
type: feature
priority: deferred
created_at: 2026-04-29T21:51:04Z
updated_at: 2026-04-30T01:17:48Z
---

## Description

Three sibling improvements to 'isaac sessions' that all touch
src/isaac/cli/sessions.clj and share a format helper. Consolidated
into one bead per planner discussion.

## Spec

features/cli/sessions.feature has three new @wip scenarios:

1. 'sessions output has aligned columns with a header row'
   - Fixed-width column layout
   - Header row uppercase (SESSION  AGE  USED  WINDOW  PCT)
   - Right-align numeric columns
   - Token counts comma-formatted
   - Pct as its own column
   - Sort order preserved (updated-at desc)

2. 'sessions show prints metadata for one session'
   - 'isaac sessions show <id>' subcommand
   - Reuses /status format via bridge/format-status (shared helper)
   - Prints Crew, Model, Session, Turns, Context — no transcript dump

3. 'sessions delete removes a session and its transcript'
   - 'isaac sessions delete <id>' subcommand
   - No confirmation prompt (typing the command is consent)
   - Removes index entry AND deletes <state-dir>/sessions/<id>.jsonl

## Step rename

Rename the existing step 'the EDN isaac file \"{path}\" does not exist'
(server.clj:564) to 'the isaac file \"{path}\" does not exist'.
The 'EDN' was misleading — the impl is just (fs/exists? path), nothing
EDN-specific. Drop the prefix entirely; replace, don't alias.

## Implementation surfaces

- src/isaac/cli/sessions.clj:
  - format-session-row + print-crew-sessions: column-aligned output
    with header row
  - 'show' subcommand: lookup session, render via bridge/format-status
  - 'delete' subcommand: remove index entry + delete JSONL
- src/isaac/session/storage.clj:
  - delete-session! function: removes index entry and JSONL
- src/isaac/main.clj:
  - route 'sessions show <id>' and 'sessions delete <id>'
- spec/isaac/features/steps/server.clj:
  - rename step + helper to drop 'EDN'

## Out of scope

- Pruning stale sessions (was the original bead framing; not what
  the user actually wanted)
- Confirmation prompt on delete (typing the command is consent)
- --full flag for sessions show to dump transcript (future)
- JSON output (future)
- Renaming /status -> /session (separate ergonomic decision)

## Related

- /status implementation in src/isaac/session/bridge.clj — share
  format helper between /status and 'sessions show'
- isaac-kf7q: per-session sidecar storage refactor (deferred;
  affects the same storage layer eventually)

## Definition of done

- All three @wip scenarios pass
- Existing 'sessions lists all sessions' and /status scenarios
  still pass
- Step renamed across all callers (no callers of 'the EDN isaac
  file ... does not exist' remain)
- bb features and bb spec green

## Notes

Verification failed: the sessions feature work itself appears implemented (sessions show/delete/formatting paths are present and bb features features/cli/sessions.feature passes), but the bead's own Definition of Done also required the step rename to be completed with no remaining callers of the old phrase. That condition is not met. spec/isaac/features/steps/server.clj still defines the old step 'the EDN isaac file "{path}" does not exist' at line 564, and feature callers remain in features/comm/discord/routing.feature:42 and features/delivery/queue.feature:26,58. The bead explicitly said 'replace, don't alias', so leaving the old step and its callers in place is an acceptance failure. Full bb spec is green. A full bb features run currently fails elsewhere, but this bead is being reopened specifically for the incomplete step rename requirement.

