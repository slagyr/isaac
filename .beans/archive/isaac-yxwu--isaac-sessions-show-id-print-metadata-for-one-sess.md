---
# isaac-yxwu
title: "isaac sessions show <id>: print metadata for one session"
status: completed
type: feature
priority: low
created_at: 2026-04-30T00:27:17Z
updated_at: 2026-04-30T00:28:00Z
---

## Description

Add a 'sessions show <id>' subcommand to print metadata for one
session. Mirrors the /status slash command format, just parameterized
by session id. Shares the format string via bridge/format-status so
the slash command and CLI subcommand stay in sync.

## Spec

features/cli/sessions.feature has a new @wip scenario:
'sessions show prints metadata for one session'

Required output: the established /status markdown shape — Session
Status header, Crew, Model, Session, Turns, Context (X / Y) lines.
Does NOT dump transcript content (Turns count is enough).

## Implementation

- src/isaac/cli/sessions.clj: add a 'show' subcommand that takes a
  session id, looks up the session, and prints via
  bridge/format-status (or the same format helper /status uses).
- src/isaac/main.clj: route 'sessions show <id>' alongside the
  existing 'sessions [--crew]' invocation.

## Out of scope

- --full flag to dump the full transcript (future)
- JSON output (future)
- Renaming /status to /session (separate ergonomic discussion)

## Related

- isaac-upve: list-format work (sibling)
- /status implementation in src/isaac/session/bridge.clj — share
  format helper

## Definition of done

- The new @wip scenario passes
- /status scenario still passes
- bb features and bb spec green

