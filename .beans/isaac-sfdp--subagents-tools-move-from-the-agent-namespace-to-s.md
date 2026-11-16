---
# isaac-sfdp
title: 'Subagents: tools move from the agent namespace to subagent (subagent__spawn/list/cancel, :subagent/*)'
status: completed
type: task
priority: high
tags:
    - agent
    - tools
created_at: 2026-11-16T02:58:12Z
updated_at: 2026-11-16T03:03:46Z
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

feature-baseline: isaac-agent cfacd4e3392090d945c5c324d8b66cabd718c269
feature-blob: isaac-agent features/tool/subagents.feature e2a0a87c514ce032f7893581c7c25c4bbc346bd5
feature-blob: isaac-agent features/tool/subagent_control.feature 78afa7ca18bf3b686051a16d8fd60b43ec80291e
feature-blob: isaac-agent features/tool/subagent_guidance.feature 7e10935abd8e4b591ae6a1485af6f57422e92224

## Landed on main (2026-11-15)

main-sha: isaac-agent eb8501d00808f4f20869b84acbd55d7f7c3489e4

Rename across manifest, builtin specs, child tool stripping, delegation hint, specs, handbook. Validation: `bb spec` + `bb jvm-spec` 2000/0, `bb features` 965/0 (one pre-existing pending), old-name grep empty, `bb bean-gate verify isaac-sfdp` PASS. Released as isaac-agent 0.1.96 (00ad784).
