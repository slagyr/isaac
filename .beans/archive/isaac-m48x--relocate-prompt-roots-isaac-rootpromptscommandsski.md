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

Prompts are a content library, not schema-validated config, so they should not live under `config/`. Move the default prompt root from `<isaac-root>/config` to `<isaac-root>/prompts`. Commands live as flat *.md files directly under prompts/; skills live as their own directories (NAME/SKILL.md + optional siblings for bundled files) directly under prompts/. Type is determined primarily by frontmatter (`type:`, `user-invocable:`); directory inference is fallback. Clean cutover — NO legacy `config/` fallback.

Follow-up to isaac-8qd5 (prompt discovery & registry).

## Change
- `isaac.prompt.catalog` (catalog.clj): default root under prompts/ is now `:generic-root` (not :typed-base). This allows a flat layout directly under prompts/: command *.md files + skill directories (NAME/SKILL.md). Type comes from frontmatter first (`type:`, `user-invocable:`); path segment inference (prompt-dir-names) as fallback.
- Switched default to generic-root to support the flat + frontmatter model (while preserving skill dir capability and subdir organization where used).
- **No back-compat:** do NOT read under config/. Move content to prompts/. `prompt-paths` / `command-paths` / `skill-paths` unaffected.
- prompt-dir-names still used for inference on organized cases.

## Scenario updates (isaac-agent/features/prompts/catalog.feature)
Swap example paths from config/ -> prompts/ (tests use a mix of direct-ish and organized paths under prompts/ + explicit `type:` in frontmatter). The feature already emphasizes `type: > user-invocable: > directory`.
- Update any `config/commands/...` / `config/skills/...` examples to `prompts/...`
- Generic and custom-dir scenarios stay valid.
No new step defs.

## Acceptance
- Default discovery reads directly under `<root>/prompts/` (commands as *.md, skills as NAME/ dirs) and project equivalent; type from frontmatter; does NOT read config/.
- `prompt-dir-names` + `*-paths` still supported.
- catalog.feature passes (its test data uses sub-paths under prompts/ + explicit types; still valid under generic-root).
- Release notes cover config/ -> prompts/.
- Zanebot migrated: commands flat under prompts/, skills as dirs under prompts/.

## Notes
Design discussion: prompts live under <root>/prompts (not config/). Commands as flat files directly in prompts/. Skills as directories directly in prompts/ (to support SKILL.md + bundled files). Type declared in frontmatter (type: preferred).
Code default changed to :generic-root on prompts/ to enable this layout.
Zanebot updated accordingly (commands flat, skills as dirs).
