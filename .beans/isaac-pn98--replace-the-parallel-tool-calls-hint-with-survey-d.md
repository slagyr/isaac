---
# isaac-pn98
title: Replace the parallel-tool-calls hint with survey-derived batching text
status: in-progress
type: task
priority: high
tags:
    - agent
    - tokens
created_at: 2026-09-22T19:56:34Z
updated_at: 2026-09-22T19:56:34Z
---

## Problem

`isaac.llm.turn-instructions/parallel-tool-calls-hint` (isaac-la8h) has had no
measurable effect: 6,561 of 6,561 assistant tool messages carried one call
before it (2026-07-08), and 19 of 19 on `isaac-work-2` (claude-opus-5 via
claude-code) on 2026-09-22 with it in place. Every single-call response is a
full-context round-trip (~290k tokens that morning).

## Survey (2026-09-22)

Public coding-agent prompts that get batching (Cursor, Cline, Codex CLI,
OpenCode anthropic/codex/default, Claude Code 2.0, Amp, Gemini CLI, Devin)
share five moves the current hint lacks: a capability statement ("you can call
several tools in one response"), default-to-parallel with the one named
exception (sequential only when A's output decides B's arguments), the cost
named in round-trips, "plan the batch before calling" + speculative reads, and
a batch bound (3–5). Windsurf, Replit, Goose and Aider carry no such language.

## Change

Replace the hint text with the survey-derived wording (Micah approved
2026-09-22). Same var, same insertion point (`prompt.builder/build-system-text`,
all providers). Keep the locate-then-read and read-once lines.

## Acceptance

- Specs that pin the hint (`builder_spec`, `anthropic_spec`, `turn_spec`) updated
  to the new text; `bb spec` green; `features/session/parallel_tool_calls.feature`
  green.
- One-time on zanebot after deploy: a read-heavy prompt on the GLM crew produces
  at least one assistant message carrying two or more tool calls (transcript
  `toolCall` count per assistant entry). Record before/after in this bean.

## Work log (2026-09-22, planner)

- isaac-agent `bean/isaac-pn98` @ 603e9f7 (from main f83af55): new hint text,
  `spec/isaac/llm/turn_instructions_spec.clj`, manifest 0.1.80, CHANGELOG.
  `bb spec` 1703/0, `features/session/parallel_tool_calls.feature` 3/0.
- Landing on main goes through PR slagyr/isaac-agent#2 (a direct push to main
  was blocked by the planner's auto-mode classifier). Micah merges.
- Deploy: agent main is 11 commits past the deployed a0a4180 (xpkf, 6doh,
  3mtu, 9af8) and weather (f3hq) has not landed, so the hint ships alone as
  `hotfix/isaac-pn98-agent-0.1.80` @ e0ced4d = a0a4180 + the same commit.
  Registry pin → e0ced4d. Do not delete that branch while the registry points
  at it.
