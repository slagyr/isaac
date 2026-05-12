---
# isaac-sqjf
title: "Implement session_model tool"
status: completed
type: feature
priority: normal
created_at: 2026-04-28T15:13:18Z
updated_at: 2026-04-28T16:09:28Z
---

## Description

Implement the session_model mutator tool. Spec:
features/tools/session_model.feature

Replaces the write half of the now-deleted session_state tool.

## Surface

- Tool name: session_model (snake_case)
- LLM-facing parameters:
  - model (string, optional): alias to switch to
  - reset (boolean, optional): true reverts to crew's currently
    configured model
  - model and reset are mutually exclusive
- Runtime auto-injects :session-key and :state-dir
- Returns the same JSON shape as session_info (so the LLM sees the
  new state as confirmation)

## Error cases

- Both model and reset provided: error
  "model and reset are mutually exclusive"
- model alias not in :models config: error
  "unknown model: <alias>"

## Implementation notes

- Treat blank-string model ("") as not-provided (defensive: LLMs
  over-eagerly fill schemas). This is per-tool — no global helper
  per project decision; just check at the top of the handler.
- Persist the override to the session record so future turns use it.
- reset clears the per-session :model so the lookup falls through to
  crew's :model. If you store the absence as nil, ensure read-side
  treats nil as "use crew default" cleanly.

## Depends on

- isaac-lwtd (session_info) ships first and removes the old
  session_state implementation from src/isaac/tool/builtin.clj
  along with shared step infrastructure (current-session step,
  JSON-table step, etc.). Reuse those steps; the deletion is
  already done.

## Definition of done

- bb features features/tools/session_model.feature passes all 4 scenarios
- @wip removed
- session_model registered in the built-in tool registry
- bb features full suite green
- bb spec full suite green

## Crew config update needed

zanebot's crew/marvin.edn currently lists :session-state in tools.allow.
That value will not match anything once the rename ships. Update to
:session_info and/or :session_model after deploy. Note for the
operator, not part of code DoD.

