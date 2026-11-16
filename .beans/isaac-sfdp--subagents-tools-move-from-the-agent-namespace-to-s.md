---
# isaac-sfdp
title: 'Subagents: tools move from the agent namespace to subagent (subagent__spawn/list/cancel, :subagent/*)'
status: draft
type: task
priority: high
tags:
    - agent
    - tools
created_at: 2026-11-16T02:58:12Z
updated_at: 2026-11-16T02:58:12Z
parent: isaac-4qxr
---

Repo: **isaac-agent**. Follows isaac-p5d3, isaac-urog, isaac-fnyp (all landed, none deployed yet).

Micah, 2026-11-15: the subagent tools live in the `agent` namespace, which is too broad — `isaac.agent` is the module and every crew is an agent. Everything else already says subagent (factories `subagent-*-tool-factory`, logs `:subagent/started`/`:subagent/ended`, origin `:subagent`, handbook "Subagents").

## Change

- Tool namespace `agent` → `subagent`: `agent__spawn` / `agent__list` / `agent__cancel` become `subagent__spawn` / `subagent__list` / `subagent__cancel`; crews grant them with `:subagent/*` (or `:subagent/spawn` …).
- Everywhere the names appear: manifest tool entries (`resources/isaac-manifest.edn`), builtin tool specs/descriptions (`tool/builtin.clj`), child tool stripping (`drive/request.clj`, `drive/tools_config.clj`), `turn-instructions/spawn-tool?` and the delegation hint text, specs, and the handbook chapter.
- No alias for the old names — they were never deployed.

## Acceptance

All three subagent feature files are re-cut with the new names (feature-level `@wip`; remove it from each when it passes):

- `bb features features/tool/subagents.feature`
- `bb features features/tool/subagent_control.feature`
- `bb features features/tool/subagent_guidance.feature`

One-time check (not a scenario): `grep -rnE '\bagent(__|/)(spawn|list|cancel)' src resources spec features` prints nothing.
