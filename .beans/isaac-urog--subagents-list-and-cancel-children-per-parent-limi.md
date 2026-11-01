---
# isaac-urog
title: 'Subagents: list and cancel children, per-parent limit, started/ended logs'
status: draft
type: feature
priority: normal
tags:
    - agent
    - tools
created_at: 2026-11-01T18:45:06Z
updated_at: 2026-11-01T18:45:06Z
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

## Acceptance (to become scenarios)

- list shows a running child, then shows it done after its result is delivered.
- cancel ends a running child and the parent receives a cancelled result.
- a fifth spawn with four running fails with the limit error.
- started/ended log lines carry the fields above.
