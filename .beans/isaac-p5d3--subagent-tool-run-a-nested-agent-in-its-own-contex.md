---
# isaac-p5d3
title: 'Subagents: agent__spawn runs a child turn in its own session and delivers its result to the parent'
status: in-progress
type: feature
priority: high
tags:
    - agent
    - tools
created_at: 2026-09-10T03:21:04Z
updated_at: 2026-11-02T14:11:03Z
parent: isaac-4qxr
blocked_by:
    - isaac-cgz9
---

Repo: **isaac-agent** (tool registry + turn queue).

## Why

A crew's context is its scarcest resource. Every search lands file dumps in its transcript, which costs compactions, which cost re-reads (tono-jlzi, 2026-09-09: 798 reads of 85 files, nine compactions, dead-lettered short of handoff). And a crew busy with a long task can't talk to the person. A subagent fixes both: the work happens in another context, the parent keeps only the conclusion, and stays free to talk meanwhile.

## Design (settled 2026-11-01 with Micah)

- **Watch, don't wait.** `agent__spawn` starts a child and returns a handle at once. The parent keeps working or ends its turn and talks to the person. No blocking wait mode for now.
- **A child is an ordinary queued turn** (`turn/submit`) on a new session tagged with its parent. It gets resource-pool admission and cancellation like any turn.
- **Configurable per spawn.** The child inherits the parent's crew by default. The spawn call can override: `crew`, `model` (any configured model alias — cheaper for light work, stronger for hard work), and `effort`. The tool description lists the available model aliases; an unknown alias is a tool error naming the choices. Other crew settings come from the chosen crew.
- **Result delivery.** When the child's turn ends, an observer submits a result to the parent session with `origin {:kind :subagent}`: the child's final message, one stats line (crew, model, cycles, tokens, whether it hit its cap), and the child's session id so the parent can read the transcript if it needs to. If the parent is idle, the result starts a turn; if busy, it joins the running turn (mid-turn input bean).
- **Depth 1.** A child cannot spawn children (`agent__spawn` is not available to it).
- **Cancel.** Cancelling the parent's turn does not cancel background children; they're cancelled directly (list/cancel bean).

## Follow-ons

- list / cancel / per-parent limit / logs — separate bean.
- delegation prompting — separate bean.
- Model router (isaac-d3ta) could later pick the child's model; out of scope.

## Acceptance

Scenarios in `isaac-agent/features/tool/subagents.feature` (feature-level `@wip`; remove it when all pass):

- `bb features features/tool/subagents.feature:22` — spawn returns a handle at once; child session tagged `:parent`, inherits crew; nothing runs yet
- `bb features features/tool/subagents.feature:35` — child's result reaches the idle parent as an `origin.kind :subagent` turn: final message + stats line (`model: … cycles: … session: …`)
- `bb features features/tool/subagents.feature:53` — `model` + `effort` overrides reach the provider request
- `bb features features/tool/subagents.feature:70` — `crew` override: child takes that crew's model and soul
- `bb features features/tool/subagents.feature:91` — unknown model alias is a tool error naming configured aliases; no child session created
- `bb features features/tool/subagents.feature:100` — a child's tool list has no `agent__spawn`

Run with `-t wip` until `@wip` is removed, or the whole file: `bb features features/tool/subagents.feature`.

Also: sessions gain a `:parent` field (schema `:description`); `agent__spawn` gets a tool description listing configured model aliases; update the isaac-agent handbook chapter (`resources/isaac/agent/handbook.md`) with subagents: what spawn does, overrides, how results arrive.

Depends on isaac-cgz9 (subagent results joining a busy parent; result ordering). Land after it.

feature-baseline: isaac-agent 71edcfea919d59ec9cf44df91a364ee3e891db10
feature-blob: isaac-agent features/tool/subagents.feature faf53200c4e1e099719727aa95485fd3c480be0c

## Work checkpoint (2026-11-02, scrapper@bean-isaac-p5d3)

Done: implementation committed/pushed on isaac-agent bean/isaac-p5d3 at 497a676; all six subagent scenarios green (18 assertions), focused subagent specs green, lint no errors. Child sessions, overrides, result observer, depth restriction and handbook implemented.
Next: full `bb ci` is still running; spec suite green (1986 examples), full feature run shows F markers. Inspect `/tmp/p5d3-ci.log` failure summary; resume at `src/isaac/agent/tool/subagent.clj:24` and `src/isaac/agent/turn/worker.clj:150`. Fix failures, rerun `bb ci`, gate, land.
