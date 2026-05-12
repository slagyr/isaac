---
# isaac-x5vo
title: "Implement session_state tool"
status: completed
type: feature
priority: normal
created_at: 2026-04-28T00:59:04Z
updated_at: 2026-04-28T02:55:24Z
---

## Description

Implement the session_state built-in tool. Spec: features/tools/session_state.feature

Lets the LLM answer "what model are you?" and switch its own model
mid-conversation. Crew is read-only by design — switching crews would
let the agent escalate its tool permissions.

## Tool surface

- session-key (required): which session
- model (optional): alias to switch to
- reset-model (optional): boolean; clear per-session override, fall back to crew's current :model
- model and reset-model are mutually exclusive

## Result shape (JSON)

  {:crew "main"
   :model {:alias "grover" :upstream "echo"}
   :provider "grover"
   :session "status-test"
   :origin {:kind "cli"}                    ; or {:kind "webhook" :name "lettuce"}
   :created-at "2026-04-27T10:00:00Z"
   :updated-at "2026-04-27T10:00:00Z"
   :context {:used 0 :window 32768}
   :compactions 0}

## Pre-work bundled into this bead

1. Add :createdAt to session record at storage/create-session!
   (today only :updatedAt exists)
2. Webhook session creation must set :origin {:kind :webhook :name "<hook-name>"}
   (note for isaac-vb8m hooks bead — set when that ships)
3. New step phrase: `the tool result JSON has:` with `path | value` rows
   supporting dotted paths (e.g. model.alias, context.window)
4. Extend `the following sessions exist:` step to honor `origin.kind`
   and `origin.name` columns

## Definition of done

- `bb features features/tools/session_state.feature` passes all 6 scenarios
- @wip removed
- Full `bb features` suite still green
- Tool registered in built_in registry; visible via /tools listing

## Future / out of scope

- Kebab-case rename of session index fields (compactionCount, inputTokens
  etc.) is isaac-48dx, not blocking this bead
- Full origin coverage (cron sub-name, acp sub-name) — covered by webhook
  scenario as proxy; add more if shape needs to differ

