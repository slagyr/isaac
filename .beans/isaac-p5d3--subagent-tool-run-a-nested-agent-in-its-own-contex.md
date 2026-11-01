---
# isaac-p5d3
title: 'Subagents: agent__spawn runs a child turn in its own session and delivers its result to the parent'
status: draft
type: feature
priority: high
tags:
    - agent
    - tools
created_at: 2026-09-10T03:21:04Z
updated_at: 2026-11-01T18:45:06Z
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

## Acceptance (to become scenarios)

- spawn returns a handle immediately; the parent's turn ends while the child is still running.
- the child runs on its own session, tagged with the parent; its tool calls are not in the parent's transcript.
- with no overrides the child uses the parent's crew and model; with `model` given it uses that model; with `crew` given it uses that crew.
- an unknown model alias fails the spawn with an error listing configured aliases.
- when the child finishes and the parent is idle, the parent gets one new turn whose input is the labelled result (final message, stats, child session id).
- a child's tool list has no `agent__spawn`.
