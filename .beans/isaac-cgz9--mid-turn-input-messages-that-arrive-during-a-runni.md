---
# isaac-cgz9
title: 'Mid-turn input: messages that arrive during a running turn join it at the next cycle'
status: draft
type: feature
priority: high
tags:
    - agent
created_at: 2026-11-01T18:45:06Z
updated_at: 2026-11-01T18:45:06Z
parent: isaac-4qxr
---

Repo: **isaac-agent** (turn queue + tool loop).

## Why

Micah, 2026-11-01: when a turn is running and new input arrives for the same session, it should join the running turn, not wait behind it. The model asks "What's your favorite color?", the person says "Red", then a second later "Wait no, blue": the reply should account for blue. The same mechanism carries subagent results into a busy parent (isaac-p5d3). New input informs the turn; it does not take it over.

## Design

- **Cycle boundary.** `llm/tool_loop.clj` already has an `after-tools` hook between a tool batch and the next model call. There, take every request waiting on this session (`queue/waiting-groups`, state `:waiting-session`) and append each as a user message.
- **Labelled by source.** Each absorbed message says where it came from: a person's message ("new message from <name>") or a subagent result (handle + stats). The framing tells the model this is new information for the current task.
- **Absorbed, not rerun.** The absorbed queue records are marked absorbed (new state) so they never run as their own turn.
- **Final-cycle arrivals.** If input arrives during the model call that would end the turn (no tool calls), run one more cycle with the input appended instead of ending. On a streaming comm the first reply may already be out; it stays, and the extra cycle corrects it.
- **Person first.** When a session is idle and several requests wait, a person's message runs before subagent results.

## Out of scope

- The Claude-code lane (the Claude CLI drives its own tool loop) — follow-up bean.

## Acceptance (to become scenarios)

- A message arriving while a turn is between tool cycles appears in that turn's transcript as a labelled user message before the next model call, and no separate turn runs for it.
- A message arriving during the final model call causes one more cycle; the turn's last reply follows the newer message (red → blue).
- With a person's message and a subagent result both waiting on an idle session, the person's message runs first.
- A cancelled turn absorbs nothing; waiting input runs as its own turn afterward.
