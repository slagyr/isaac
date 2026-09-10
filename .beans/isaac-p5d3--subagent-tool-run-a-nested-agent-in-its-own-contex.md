---
# isaac-p5d3
title: 'Subagent tool: run a nested agent in its own context and return only its conclusion'
status: draft
type: feature
tags:
    - agent
    - tools
created_at: 2026-09-10T03:21:04Z
updated_at: 2026-09-10T03:21:04Z
---

Repo: **isaac-agent** (tool registry + drive). Draft — design to be settled
in a planning session before scenarios.

## Why

A worker's context is its scarcest resource. Every search it runs lands the
file dumps in its own transcript, which then costs a compaction, which then
costs re-reads. tono-jlzi (2026-09-09) spent 1525 tool calls over 480 cycles:
798 file reads of only 85 distinct files (39 read four or more times,
handlers.clj 106 times), nine compactions in a day, and dead-lettered two
commands short of its handoff. Half the budget was re-learning what earlier
context had already learned.

Claude Code hands that work to a subagent: a tool call that runs a nested
agent with its own context and returns only its final message. The caller
keeps the conclusion, not the file dumps. Isaac has no equivalent; hail is
the async cousin, but its results arrive as new turns, which is the wrong
shape for "go find X and tell me".

## Sketch (not decided)

A tool `agent__run` (name TBD), available to crews that allow it:

- **Input:** `prompt` (the task), optional `crew` / `model` (default: a
  cheaper model than the caller, e.g. the crew's `:subagent-model` or the
  caller's model), optional `tools` allowlist (default: read-only — fs/read,
  fs/grep, fs/glob, web/*; no exec, no comm, no hail), optional `cwd`
  (default: caller's cwd).
- **Run:** synchronous inside the caller's turn. The child gets a fresh,
  ephemeral session (`<parent>/sub-<id>`), the caller's directory bounds,
  its own cycle limit (small, e.g. 40), no episodes, no recall injection,
  no hooks. Depth 1 only — a subagent cannot spawn subagents.
- **Output:** the child's final assistant message plus `{:cycles N
  :tool-calls N :tokens N}`; on cap or error, whatever it produced so far
  with `:exhausted true`. The child's transcript is discarded (or kept under
  the parent's session dir for debugging, decide).
- **Parallel:** independent `agent__run` calls in one batch run
  concurrently under the existing tool-batch executor (j2v0); the child
  turns do not count against the crew's `:max-in-flight`? (decide — they
  are real provider calls).
- **Cancel:** bridge cancel on the parent cancels children (cancellation
  token propagates).
- **Cost:** child tokens roll up into the parent's turn accounting and the
  transcript gets one row per call (tool call + result), like any tool.

## Open questions for the planning session

1. Sync in-turn (simple, blocks the parent) vs async (returns a handle, later
   `agent__wait`)? Recommend sync first; async is what hail already does.
2. Does a child count against `:max-in-flight`? It holds a provider
   connection, so probably yes — or a separate `:max-subagents`.
3. Default model: caller's model, or a designated cheaper one? A search
   agent on grunt-class is the whole point of the cost argument.
4. Prompt shape for the child: crew soul + the task, or a dedicated
   "researcher" soul that reports conclusions and never edits?
5. Where the child's transcript lives, and whether logs carry
   `:subagent/started` / `:subagent/ended` with parent session and cycles.
6. Prompting the parent: turn_instructions gains "delegate sweeps that span
   many files to agent__run; keep the conclusion, not the dumps" once the
   tool exists.

## Related

- j2v0 parallel tool batches (executor the children would run under).
- openclaw parity epic isaac-0jy lists "Subagents — spawn sub-agents for
  parallel work" as deferred.
- Turn-instruction batching/read-discipline rewrite (2026-09-10) reduces the
  same waste without delegation; measure again after it ships before
  sizing this.

## Acceptance

TBD with scenarios. Candidates: a subagent that greps and reads three
files returns one message and the parent transcript has exactly one tool
call + one result row; a child hitting its cycle cap returns partial output
flagged `:exhausted`; a child cannot call agent__run; cancel on the parent
ends the child; two subagents in one batch run concurrently (rendezvous
mocks from j2v0).
