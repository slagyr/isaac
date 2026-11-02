---
# isaac-urog
title: 'Subagents: list and cancel children, per-parent limit, started/ended logs'
status: in-progress
type: feature
priority: normal
tags:
    - agent
    - tools
created_at: 2026-11-01T18:45:06Z
updated_at: 2026-11-02T14:19:36Z
parent: isaac-4qxr
blocked_by:
    - isaac-p5d3
---

Repo: **isaac-agent**. Follows isaac-p5d3 (spawn + delivery).

The parent can see and control its children:

- `agent__list` — the parent's children: handle, crew, model, state (running / done / cancelled / failed), elapsed, cycles.
- `agent__cancel <handle>` — cancels the child's turn; the parent gets a result marked cancelled.
- **Per-parent limit** — default 4 running children (crew-configurable). Spawning past it is a tool error naming the running handles.
- **Logs** — `:subagent/started` and `:subagent/ended` with parent session, child session, crew, model, cycles, tokens, outcome.

## Design notes (2026-11-02)

- `agent__list` returns `{"children": [...]}`: handle, crew, model (effective alias), state (`queued` / `running` / `done` / `cancelled` / `failed`), cycles.
- `agent__cancel {handle}` works on a queued or running child; the parent still gets a result turn whose text says the child was cancelled.
- Limit: crew key `:max-subagents`, default 4, counts the parent's unfinished children (queued + running). Error text: `subagent limit reached (N running)`. Schema entry carries a `:description`.
- Logs: `:subagent/started` (parent, session, crew, model) and `:subagent/ended` (+ cycles, tokens, outcome).

## Acceptance

Scenarios in `isaac-agent/features/tool/subagent_control.feature` (feature-level `@wip`; remove it when all pass):

- `bb features features/tool/subagent_control.feature:23` — agent__list shows children: queued before the tick, done with cycles after
- `bb features features/tool/subagent_control.feature:44` — agent__cancel stops a child; parent gets a cancelled result turn; list shows `cancelled`
- `bb features features/tool/subagent_control.feature:66` — third spawn past `:max-subagents 2` fails, no session created; spawning works again once children finish
- `bb features features/tool/subagent_control.feature:90` — `:subagent/started` / `:subagent/ended` log lines with parent, session, crew, model, cycles, tokens, outcome

Run with `-t wip` until `@wip` is removed, or the whole file: `bb features features/tool/subagent_control.feature`.

Step extensions (in `spec/isaac/agent/tool/tools_steps.clj`): `the tool result JSON has:` keeps `#"…":name` captures, and `the tool "…" is called with:` resolves `#name` cells from them (scenario :44).

Also: update the isaac-agent handbook chapter (`resources/isaac/agent/handbook.md`) with list, cancel and `:max-subagents`.

Depends on isaac-p5d3 (agent__spawn). Land after it.

feature-baseline: isaac-agent 8a9d4db7f263b59dcc5f5699056f2582f76d3b25
feature-blob: isaac-agent features/tool/subagent_control.feature 19062bef6f325e72d628595a80f80bc6a47b66da
