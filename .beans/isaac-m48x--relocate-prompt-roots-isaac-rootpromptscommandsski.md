---
# isaac-m48x
title: 'Relocate prompt roots: <isaac-root>/prompts/{commands,skills} (drop config/)'
status: unverified
type: task
priority: normal
tags:
    - unverified
created_at: 2026-06-22T23:07:49Z
updated_at: 2026-06-22T23:20:47Z
parent: isaac-nwj3
---

Prompts are a content library, not schema-validated config, so they should not live under `config/`. Move the default prompt typed-base from `<isaac-root>/config` to `<isaac-root>/prompts`, keeping the `commands/` + `skills/` subdir convention. Clean cutover — NO legacy `config/` fallback (Micah, 2026-06-22).

Follow-up to isaac-8qd5 (prompt discovery & registry).

## Change
- `isaac.prompt.catalog` (catalog.clj ~line 220): the global typed-base root is `{:layer :global :mode :typed-base :path (str root "/config")}`. Change `(str root "/config")` -> `(str root "/prompts")`. The `prompt-dir-names` default (`{commands->command, skills->skill}`) is unchanged, so it now scans `<root>/prompts/commands/` and `<root>/prompts/skills/`.
- **Project symmetry:** the project layer's default typed-base should likewise be `<project-root>/prompts/{commands,skills}` (mirror the global change in the project-roots construction).
- **No back-compat:** do NOT also read `<root>/config/{commands,skills}`. Existing installs must move their prompts to `prompts/`; call this out in release notes / CHANGELOG. Configurable extra roots (`:prompt-paths`/`:command-paths`/`:skill-paths`) are unaffected.
- **Scaffolding:** if `isaac init` (or any scaffold) creates `config/commands`/`config/skills`, repoint it to `prompts/commands`/`prompts/skills`. (Verify.)

## Scenario updates (isaac-agent/features/prompts/catalog.feature)
These must land WITH the code change (don't edit ahead — they'd go red). Swap the default-base path `config/` -> `prompts/`:
- L17  `config/commands/work.md`        -> `prompts/commands/work.md`
- L32  `config/skills/tdd/SKILL.md`      -> `prompts/skills/tdd/SKILL.md`
- L46  `config/commands/helper.md`       -> `prompts/commands/helper.md`
- L79  `config/skills/clojure/SKILL.md`   -> `prompts/skills/clojure/SKILL.md`
- L92  `config/commands/work.md` (global side of project-shadow) -> `prompts/commands/work.md`
- L136 `config/abilities/refactor.md` (prompt-dir-names custom mapping) -> `prompts/abilities/refactor.md`
- L65  `config/prompts/review.md` — this tests a `:prompt-paths` GENERIC root (user-configured), not the default base. Keep the scenario, but rename the example path away from `config/...` (e.g. a neutral `vendor/prompts/review.md`) to avoid implying `config/` is still special.
- Project-shadow (L91) and walk-up (L116): update any project-side prompt paths to the `prompts/` convention to match.
No NEW step defs — same steps (`the isaac file "..." exists with:`, `the prompt catalog is resolved`, `the prompt catalog contains:`).

## Acceptance
- Default discovery reads `<root>/prompts/{commands,skills}` (global) and `<project>/prompts/{commands,skills}` (project); does NOT read `config/{commands,skills}`.
- `prompt-dir-names` + configurable `*-paths` roots still work, now rooted at `prompts/`.
- catalog.feature updated per above and green (no @wip regressions).
- Release notes document the breaking move (config/ -> prompts/).

## Notes
Design discussion 2026-06-22: prompts are library content (markdown bodies the agent draws from), distinct from `config/` which holds schema-validated settings + entity definitions (crew/providers/comms/cron/hooks). Chose subdir layout (A) `prompts/{commands,skills}` over a flat `prompts/*.md`+type-frontmatter (B).
Implemented in isaac-agent @ ca59165.
