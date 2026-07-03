---
# isaac-k4mf
title: 'Hail work turns must not silently complete on empty terminal model responses'
status: draft
type: bug
priority: high
created_at: 2026-07-03T15:55:00Z
updated_at: 2026-07-03T15:55:00Z
---

## Problem

On zanebot, `isaac-work-1` stopped active bean work twice without any explicit
completion, handoff, or blocked signal:

- `isaac-a9vf` made real progress, edited files, and passed focused spec checks
  locally, then the turn ended with an empty assistant message.
- `isaac-n5r2` was then accepted on the same session, claimed, began
  investigation, and also ended with an empty assistant message.

The current runtime clears session in-flight state when a turn returns without
`tool-calls` and without `:error`, even if the final assistant content is empty.
That makes the session eligible for new hail delivery while bean work is still
unfinished.

## Investigation findings (2026-07-03)

- This was **not** mid-turn interruption. `isaac-a9vf` completed a turn, then
  `isaac-n5r2` was delivered on the next turn.
- The real orchestration failure is that a hail-driven work turn can end with:
  - executed tools
  - no final handoff / commit / blocked signal
  - empty assistant content
  - no runtime error
- Isaac currently accepts that shape as a normal completed turn.

## Desired behavior

For hail-driven bean work, an empty terminal model response is not enough to
prove the task is done.

The system should require explicit completion evidence before treating the turn
as successfully finished and making the session available for new bean delivery.

Examples of acceptable completion evidence:

- a verify-band or plan-band hail was sent
- a bean status/notes commit was made and pushed
- a non-empty assistant completion summary is present
- an explicit blocked/conflict escalation was recorded

## Loop risk

Yes, there is loop risk if the fix is "just keep asking the model again until it
talks."

Any recovery behavior must be bounded and outcome-based. Likely shape:

- detect suspicious terminal result (`executed-tools > 0`, `content == ""`,
  no handoff / no bean-side effect)
- allow at most a small bounded retry budget
- then fail the turn explicitly if completion evidence is still missing

Do not allow unbounded reprompt loops.

## Likely repo scope

- `isaac-agent`
  - `src/isaac/llm/tool_loop.clj`
  - `src/isaac/drive/turn.clj`
- possibly hail / orchestration surfaces if completion evidence needs explicit
  tagging or metadata

## Notes

- This is related to, but separate from, the in-flight / session occupancy
  question. Even with perfect occupancy rules, a silent empty completion is
  still a bad terminal state for hail-driven work.
- This bean is intentionally `draft` until we settle the exact acceptance
  contract and bounded-retry behavior in scenarios.
