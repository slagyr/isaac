---
# isaac-fnyp
title: 'Subagents: turn instructions teach crews when and how to delegate'
status: completed
type: task
priority: normal
tags:
    - agent
created_at: 2026-11-01T18:45:06Z
updated_at: 2026-11-16T00:42:02Z
parent: isaac-4qxr
blocked_by:
    - isaac-p5d3
    - isaac-urog
---

Repo: **isaac-agent** (turn instructions). Follows isaac-p5d3 (spawn + delivery) and isaac-urog (list/cancel), both landed.

Once crews can spawn subagents, tell them how to use them: hand off file sweeps and research that would flood the context, keep only the conclusion, stay available to the person while children run, pick a cheaper model for simple work and a stronger one for hard work, and don't spawn for something a few tool calls would answer.

## Design (2026-11-15, Micah)

- A second standing hint in `src/isaac/agent/llm/turn_instructions.clj`, beside `parallel-tool-calls-hint`. It opens with the heading `Delegating to subagents:`.
- Included only when `agent__spawn` is in the turn's **effective** tool list — the list after child sessions lose the spawn tools (`drive/request.clj:87`), not the crew's config. So children never get it.
- Both system-prompt paths: `llm/prompt/builder.clj` (`build-system-text`) and the Anthropic path `llm/api/messages.clj:76`. Compaction requests (`include-tool-batching-hint? false`) don't get it either.
- Guidance text (unit-spec its key lines in `spec/isaac/agent/llm/turn_instructions_spec.clj`, the way the batching hint is specced):

```
Delegating to subagents:
- agent__spawn runs a task in a separate context and sends you its conclusion
  when it finishes. Use it for work that would flood your context: sweeps across
  many files, broad searches, research that reads a lot to answer a little.
- Don't spawn for what a few tool calls would answer; do that yourself.
- The child sees only your prompt. Brief it fully: the goal, what you already
  know or ruled out, where to look, and what to report back.
- Fit the model to the work: a cheaper model for simple, mechanical tasks, a
  stronger one for hard reasoning.
- Spawning doesn't block. Keep working, or end your turn and stay available to
  the person; the result arrives as its own message. Don't redo delegated work.
```

- Update the isaac-agent handbook chapter (`resources/isaac/agent/handbook.md`) subagents section: crews with `agent__spawn` get this guidance.

## Acceptance

Scenarios in `isaac-agent/features/tool/subagent_guidance.feature` (feature-level `@wip`; remove it when all pass):

- `bb features features/tool/subagent_guidance.feature:14` — a crew with `agent__spawn` gets `Delegating to subagents:` in its system prompt
- `bb features features/tool/subagent_guidance.feature:22` — a crew without it doesn't
- `bb features features/tool/subagent_guidance.feature:30` — a subagent child doesn't, even when its crew allows spawn

Run the whole file: `bb features features/tool/subagent_guidance.feature` (remove `@wip` first — the runner excludes it).

## After deploy (not a gate)

Rerun a sweep-heavy task (the tono-jlzi pattern) on zanebot and compare parent context growth and compactions before and after.

feature-baseline: isaac-agent 9831daa1905f30e4d8e77cc0401ebc6fe269e51c
feature-blob: isaac-agent features/tool/subagent_guidance.feature 864771c92cb7f86143d095e0a2066b6b92fac503

## Landed on main (2026-11-15)

main-sha: isaac-agent 0b6ba6e

Implementation: `subagent-delegation-hint` + `spawn-tool?` in turn_instructions; `build-system-text` takes the turn's tools and adds the hint after the batching hint when agent__spawn is present (both builder and Anthropic paths; compaction excluded). Handbook updated. Validation: `bb spec` + `bb jvm-spec` 2000/0, `bb features` 965/0 (one pre-existing pending), `bb bean-gate verify isaac-fnyp` PASS.
