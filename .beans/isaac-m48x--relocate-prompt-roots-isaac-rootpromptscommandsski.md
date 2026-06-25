---
# isaac-m48x
title: 'Relocate prompt root: <isaac-root>/prompts (flat; type by frontmatter; drop config/)'
status: completed
type: task
priority: normal
created_at: 2026-06-22T23:07:49Z
updated_at: 2026-06-22T23:43:37Z
parent: isaac-nwj3
---

Prompts are a content library, not schema-validated config, so they should not live under `config/`. Move the default prompt root from `<isaac-root>/config` to `<isaac-root>/prompts` (flat files under prompts/, with type determined by frontmatter `type:` or `user-invocable:`, directory segments still provide inference fallback for organized layouts). Clean cutover — NO legacy `config/` fallback (Micah, 2026-06-22).

Follow-up to isaac-8qd5 (prompt discovery & registry).

## Change
- `isaac.prompt.catalog` (catalog.clj): the default prompts root is now `{:layer :global :mode :generic-root :path (str root "/prompts")}` (and symmetric for project). Files are discovered flat (or in subdirs) directly under prompts/; the primary signal for type is explicit `type: command|skill|rule` (or `user-invocable:`) in YAML frontmatter. Directory first-segment inference (via prompt-dir-names) remains as fallback.
- Default switched from :typed-base (which required subdirs) to :generic-root so flat files under prompts/ work out of the box.
- **No back-compat:** do NOT also read `<root>/config/{commands,skills}`. Existing installs must move their prompts to `prompts/`; call this out in release notes / CHANGELOG. Configurable extra roots (`:prompt-paths`/`:command-paths`/`:skill-paths`) are unaffected.
- prompt-dir-names still supported for custom organized layouts or :*-paths.

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
- Default discovery reads flat (or organized) files under `<root>/prompts/` (global) and `<project>/prompts/` (project) with type from frontmatter; does NOT read `config/`.
- `prompt-dir-names` (for inference) + configurable `*-paths` roots still work.
- catalog.feature continues to pass (scenarios use explicit type: or dir fallback; subdir paths still valid).
- Release notes document the breaking move (config/ -> prompts/).
- Zanebot (and similar) migrated to flat prompts/ with explicit types.

## Notes
Design discussion 2026-06-22: prompts are library content (markdown bodies the agent draws from), distinct from `config/` which holds schema-validated settings + entity definitions (crew/providers/comms/cron/hooks). Default layout under prompts/ is flat; type determined by frontmatter (type: preferred; user-invocable and dir-segment as fallbacks).
(The earlier subdir convention under prompts/ was adjusted to support the flat + frontmatter-primary goal.)
Implemented in isaac-agent. Zanebot migrated to flat.
