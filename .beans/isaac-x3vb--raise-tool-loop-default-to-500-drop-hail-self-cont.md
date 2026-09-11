---
# isaac-x3vb
title: Raise tool-loop default to 500; drop hail self-continuations
status: completed
type: task
priority: high
created_at: 2026-08-23T02:19:14Z
updated_at: 2026-08-25T18:19:54Z
---

Likely repos: **isaac-agent** (default), **orchestration/isaac-beans** skills, **isaac** toolbox hail-bean-work. Hail runtime already dropped continuations (isaac-fgo0).

## Problem

Hail-bean-work tells crews to hail themselves EARLY with a 5-continuation budget. work-1/work-2 burn that budget writing state novels instead of finishing. Runtime hail re-queue is already gone (fgo0). Default tool-loop-max is 100.

## Settled design (2026-08-23, Micah)

- **default-max-loops = 500** (5×). Crew `:tool-loop-max` still overrides.
- **No self-continuation hails.** Do not send continuation EARLY. Do not hail your own session to keep working.
- Turn end states: done + verify/plan handoff, or **HOLD + human escalate** if still unfinished (loop cap, blocked). Keep k4mf empty-terminal nudge (not a hail continuation).
- Stale comments in `tool_loop_limit.feature` that mention 5ru9 re-queue must match fgo0 (delivered, no re-queue).

## Acceptance

1. `isaac.llm.tool-loop/default-max-loops` is 500. `bb spec spec/isaac/llm/tool_loop_spec.clj`
2. Manifest `:tool-loop-max` description says default 500.
3. hail-bean-work / plan / verify / harden skills no longer instruct session-direct continuation hails or \"N of 5\".
4. hail-bean-work states: do not hail yourself to continue; 500-loop default; HOLD if still unfinished.

## Out of scope

- Changing k4mf empty-terminal nudge
- Reintroducing hail delivery re-queue


## Implementation (2026-08-23)

- isaac-agent \`09e1ceb\`: \`default-max-loops\` 500
- hail runtime already no-requeue (fgo0)
- orchestration hail-bean-{work,plan,verify,harden}: no self-continuation
- isaac toolbox hail-bean-work: same
