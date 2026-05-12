---
# isaac-ylq0
title: "isaac sessions delete <id>: remove a session and its transcript"
status: completed
type: feature
priority: low
created_at: 2026-04-30T00:27:17Z
updated_at: 2026-04-30T00:28:01Z
---

## Description

Add a 'sessions delete <id>' subcommand to remove a session from
the index and delete its transcript file. No interactive confirmation
prompt — typing the command is the consent (matches rm/git-branch-D
conventions).

## Spec

features/cli/sessions.feature has a new @wip scenario:
'sessions delete removes a session and its transcript'

Behavior:
- Removes the session entry from the index
- Deletes <state-dir>/sessions/<id>.jsonl
- Returns exit 0 on success
- Returns nonzero with a clear error if the id doesn't exist
  (scenario for that not yet specced; add if behavior matters)

## New step phrase

  the isaac file \"{path}\" does not exist

Generic file-not-exist check. The existing
'the EDN isaac file ... does not exist' (server.clj:564) is
misleadingly named — the impl is just (fs/exists? path), nothing
EDN-specific. Either rename it or alias to the new generic phrase
as part of this work.

## Implementation

- src/isaac/cli/sessions.clj: add a 'delete' subcommand
- src/isaac/session/storage.clj: add (delete-session! state-dir id)
  that removes the index entry and deletes the JSONL
- src/isaac/main.clj: route 'sessions delete <id>'

## Out of scope

- Soft delete / recovery (out of scope; users can restore from
  DoltHub or backups if a transcript is canonical there)
- Bulk delete (future)
- Confirmation prompt (rejected; the command itself is consent)

## Related

- isaac-upve: list-format work (sibling)

## Definition of done

- The new @wip scenario passes
- The renamed/added 'the isaac file does not exist' step works
- bb features and bb spec green

