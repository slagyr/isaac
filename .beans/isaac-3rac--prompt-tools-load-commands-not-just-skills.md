---
# isaac-3rac
title: Prompt tools load commands, not just skills
status: completed
type: feature
priority: normal
created_at: 2026-10-01T17:23:56Z
updated_at: 2026-10-01T18:59:35Z
---

Micah, 2026-10-01. Crews can list and load skills (`skill__list`, `skill__load`) but not commands: `~/.isaac/prompts/commands/*.md` (`verify`, `work`, `plan`, `harden`, …) have no tool. Found on zanebot: perceptor (verifier) learned from its own memory notes to `fs__read ~/.isaac/prompts/commands/verify.md`, which its `:cwd`-only directory grant correctly refuses.

## Intent
Expand the prompt tools so a crew can list and load commands the same way it loads skills (and consider rules). Band/hail prompts then say "load the verify command" instead of naming a file path.

## Open questions (plan before todo)
- One tool family (`prompt__list`/`prompt__load` with a kind: skill | command | rule) or a sibling pair (`command__list`/`command__load`)? Grants: does `:skill/*` cover commands or is it a new grant?
- Where do commands come from: the same prompt roots as skills (`prompts/commands`, module-contributed)? Same precedence rules?
- Does a loaded command carry arguments ($ARGUMENTS-style) or is it plain text?
- Update the verify/work band templates and perceptor's memory note afterwards.

## Decision + Acceptance (Micah, 2026-10-01; gated)

One merged tool family replaces the skill tools (clean cutover, no aliases): `prompt__list` (every catalog entry with its kind: skill, command, rule; line format `- <name> (<kind>): <description>`, sorted) and `prompt__load` (`name`, optional `kind`, optional `resource` for skill bundles; a command renders with its declared skills via the catalog's command renderer; unknown → error "unknown prompt: <name>"). Grant `:prompt/*` replaces `:skill/*`.
- The @wip scenarios in isaac-agent `features/prompts/prompt_tools.feature`, plus the moved scenarios in `skill_activation.feature`, `skill_resources.feature`, `tool/permissions.feature`, `tool/window_cache.feature`, pass with @wip removed.
- Update every mention of skill__list/skill__load/list_skills/load_skill in agent src, handbook chapter, README, and isaac/.toolbox skills/commands (grep). List in the bean any zanebot/skiff crew configs that grant `:skill/*` (read-only grep); the planner edits hosts.
- `bb ci`, `bb jvm-spec`, `bb jvm-features` green.

feature-baseline: isaac-agent fead24a5574b7260b74a99b3453abfbe5168013b
feature-blob: isaac-agent features/prompts/prompt_tools.feature 95ad4072b831f81eb2a7aa89d89123a51561f3e3
feature-blob: isaac-agent features/prompts/skill_activation.feature 94d5e5361a98571293e95753817e37e25901daff
feature-blob: isaac-agent features/prompts/skill_resources.feature 2f8904ac99e2d88dbed9d58ab0d1e38990338fe2
feature-blob: isaac-agent features/tool/permissions.feature 841de4d419da381bcde5ff98fbaddd3e0a69a7c1
feature-blob: isaac-agent features/tool/window_cache.feature cc9e28553b9758b7dc6941cff188f588379bbf9f

## Worker notes (2026-10-01)

- `prompt__list`/`prompt__load` implemented in `isaac-agent` (new
  `src/isaac/agent/tool/prompt.clj`, replacing `skill.clj`); catalog gained
  `find-entry`/`resolve-entry-body`/`resolve-prompt-menu`; `resolve-skill-disclosure`
  now auto-grants `prompt__list` whenever the catalog has any commands or
  rules (not just over the skill-menu threshold), since commands aren't
  advertised in the cached menu text the way skills are. Manifest grant
  `:skill/load`/`:skill/list` replaced by `:prompt/load`/`:prompt/list`.
  No mentions of `skill__list`/`skill__load`/`list_skills`/`load_skill`
  remain in isaac-agent src/spec/features/README/manifest.
  `resources/isaac/agent/handbook.md` had no skill-tool mentions to update.
- Read-only grep on hosts for `:skill/` crew grants (none edited — planner's
  call):
  - **zanebot** (`~/.isaac/config`, current crew files only, `.bak-*`
    excluded): `main.edn`, `prowl.edn`, `scrapper.edn`, `tempest.edn`,
    `bebop.edn`, `keaton.edn`, `perceptor.edn`, `zane.edn`,
    `rocksteady.edn`, `qwen.edn`, `mixmaster.edn`, `ratchet.edn` all grant
    `:skill/*`.
  - **skiff** (`~/.isaac/config`): no `:skill/` grants found.
- `bb ci` and `bb jvm-spec` green. `bb jvm-features` (HOME isolated,
  `ISAAC_TEST_TIMEOUT_MS=600000` — the shared 60s helper timeout is too
  short for this suite, same as the native `features` task's documented
  600s override) ran twice: both runs hit the same pre-existing
  `turn/turn_store.feature:169` and `:185` timing flakes (isaac-2lc4,
  isaac-e9jl scenarios), unrelated to prompt tools. Native `bb features`
  (bb's own 600s-wrapped runner) was green both times with 0 failures.

## Landed on main (2026-10-01)

main-sha: isaac-agent f68f03967f3c9122a37625f0a6c4e6aabdf7b50f
