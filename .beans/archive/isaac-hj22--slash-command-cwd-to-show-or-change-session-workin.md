---
# isaac-hj22
title: "Slash command /cwd to show or change session working directory"
status: completed
type: feature
priority: low
created_at: 2026-04-29T18:46:36Z
updated_at: 2026-04-29T21:36:41Z
---

## Description

Add a /cwd slash command for human users to inspect and change a
session's working directory mid-session, mirroring how /model and
/crew work.

## Surface

  /cwd                    show current cwd
  /cwd /absolute/path     set the session's :cwd to /absolute/path
  /cwd ~/relative         expand and set
  /cwd .                  no-op (or refresh? probably no-op)

## Why human-only

cwd is a permission boundary — read/write/exec/glob/grep all
gate on it. The LLM does NOT get a session_cwd mutator (same
reasoning as session_model not having a crew switcher: agent
mutating its own cwd = self-escalation). Humans set cwd; agents
read it via session_info; agents shift workdir transiently via
exec :workdir.

## Why /cwd not /cd

Existing slash commands (/status, /model, /crew) are concept
names, working both bare (show) and with arg (set). /cd is
action-only and meaningless bare. /cwd matches the pattern and
the internal field name (we name the concept, not the verb).

## Implementation

src/isaac/session/bridge.clj
  - Register /cwd in slash-command dispatch (alongside /status,
    /model, /crew).
  - Bare /cwd: read session :cwd, return as command-text.
  - /cwd PATH: validate path exists, update session :cwd via
    storage/update-session!, return new value.
  - Optional: emit a session-update notification (memory channel
    event) so subsequent UX can render the change.

src/isaac/cli/prompt.clj and src/isaac/acp/server.clj
  - Already route slash commands via bridge/slash-command? +
    bridge/dispatch — should pick up /cwd automatically once
    the dispatch registers it.

src/isaac/comm/acp.clj
  - available_commands_update notification today lists
    ["status" "model" "crew"]. Add "cwd" so Toad sees it for
    autocomplete (acp_websocket_spec asserts this list — update
    the spec).

## Spec

Add a scenario to features/bridge/commands.feature (or similar)
asserting:
  - /cwd shows the current cwd
  - /cwd /tmp/some-existing-path sets it
  - session record reflects the new cwd
  - /cwd /no/such/path returns an error

Also update spec/isaac/acp/server_spec.clj's
"advertises available slash commands after session creation"
expectation to include "cwd".

## Definition of done

- /cwd works in the prompt CLI and over ACP
- session record's :cwd updates correctly
- ACP available_commands list includes cwd
- bb features and bb spec green

## Related

- isaac-d9am: closed; cwd already shown in session_info JSON
- isaac-w17w: open; exec defaulting to session cwd. /cwd makes
  this more useful — humans can shift the anchor mid-session.

