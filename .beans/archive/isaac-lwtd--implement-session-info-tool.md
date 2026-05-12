---
# isaac-lwtd
title: "Implement session_info tool"
status: completed
type: feature
priority: normal
created_at: 2026-04-28T15:13:17Z
updated_at: 2026-04-28T16:09:28Z
---

## Description

Implement the session_info read-only tool. Spec:
features/tools/session_info.feature

Replaces the read half of the now-deleted session_state tool.

## Surface

- Tool name: session_info (snake_case)
- LLM-facing parameters: NONE (zero advertised args)
- Runtime auto-injects :session-key and :state-dir per
  src/isaac/drive/turn.clj:518; the LLM cannot address other sessions
- Returns JSON with snake_case keys

## Result shape

  {"crew": "main",
   "model": {"alias": "grover", "upstream": "echo"},
   "provider": "grover",
   "session": "status-test",
   "origin": {"kind": "cli"},                  // or {"kind":"webhook","name":"lettuce"}
   "created_at": "2026-04-28T10:00:00Z",
   "updated_at": "2026-04-28T10:00:00Z",
   "context": {"used": 0, "window": 32768},
   "compactions": 0}

## Pre-work bundled into this bead

1. Add :createdAt to session record at storage/create-session!
   (today only :updatedAt exists). This enables created_at output.
2. New step: `the current session is {key:string}` — sets the session
   under test so When steps don't have to repeat it.
3. New step: `the tool {name:string} is called` (no colon, no table)
   — paired with the existing `is called with:` for zero-arg tools.
4. New step: `the tool result JSON has:` with `path | value` rows
   supporting dotted paths (model.alias, context.window, origin.kind).
5. Extend `the following sessions exist:` step to honor `origin.kind`
   and `origin.name` columns.
6. Webhook session creation must set :origin {:kind :webhook :name "<hook>"}
   when isaac-vb8m (hooks) ships — note for that bead's implementer.

## Definition of done

- bb features features/tools/session_info.feature passes both scenarios
- @wip removed
- session_info registered in the built-in tool registry
- The deleted session_state implementation in src/isaac/tool/builtin.clj
  is removed (do not leave it behind — features/tools/session_state.feature
  has already been deleted)
- bb features full suite green
- bb spec full suite green

## Out of scope

- session_model (mutator) is its own bead
- Future: allow inspecting a different session via an explicit arg
- Field renaming on the underlying session record (camelCase ->
  kebab in storage already done by isaac-48dx)

