---
# isaac-3dnw
title: 'isaac-agent: system-section berth — modules contribute ordered sections of the system prompt'
status: draft
type: feature
priority: normal
created_at: 2026-10-08T20:38:14Z
updated_at: 2026-10-08T20:38:14Z
parent: isaac-pcm3
---

DRAFT. Needs scenarios before it is todo. Part of the prompts-and-habits epic.

## Problem

Nothing outside `isaac-agent` can add text to the system prompt. The agent's berths are tools, tool-providers, llm-api, slash-commands, provider, provider-template, resource-pool-types, context-mode, session-observer and comm. The system text is built from four positional arguments in `isaac.agent.llm.prompt.builder/build-system-text` (soul, boot files, rules text, skill menu text), filled by direct calls to the prompt catalog in `isaac.agent.drive.turn` (around line 1407).

## Proposal

A new berth, working name `:isaac.agent/system-sections`. A contributor is called once per turn with the turn's facts (crew id, session cwd, config, root, fs) and returns:

- section text, or nil for nothing;
- optionally, tool names to grant for the turn (the skill menu grants `prompt__load` and `prompt__list` this way today).

The soul stays in the agent: it is a crew field and always comes first.

## Constraints

- **Stable order.** The system prefix is cached (isaac-s0ho). Sections need a declared order, and sections that change often must sort after those that do not.
- **Sections keep their identity.** `isaac.agent.drive.accounting` reports tokens per part (`:boot-files-tokens`, `:skill-menu-tokens`, and so on). The berth should carry a section id so accounting, `/status` and compaction keep a per-section breakdown instead of one lump.
- **Never blocks a turn.** A contributor that throws or is slow is skipped and logged; the turn proceeds. Same spirit as the ruling that turns never wait on MCP.
- **No provider knowledge.** A section is text plus tool names. Nothing API-specific crosses the berth.

## First step

Convert the existing three sections (boot files, rules, skill menu) to in-agent contributors of the new berth, with no behavior change. That proves the berth before anything moves out of the repo.

## Touch points today

`builder.clj`, `drive/turn.clj`, `drive/accounting.clj`, `session/compaction.clj`, `session/context.clj`, `session/cli.clj`, `bridge/status.clj`, `llm/api/messages.clj`, `llm/api/protocol.clj` all pass the four named pieces around.

## Likely repo scope

`isaac-agent`.
