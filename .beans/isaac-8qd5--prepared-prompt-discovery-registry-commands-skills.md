---
# isaac-8qd5
title: Prepared-prompt discovery & registry (commands + skills)
status: draft
type: feature
priority: normal
created_at: 2026-05-26T04:21:10Z
updated_at: 2026-05-26T04:31:32Z
parent: isaac-nwj3
---

MVP foundation for isaac-nwj3. Discover commands/skills from layered roots and resolve a per-turn catalog.

## Scope
- **Roots:** install global `~/.isaac/config/{commands,skills}/` loaded once at startup; **project** `<root>/.isaac/{commands,skills}/` resolved **per turn**; configurable extra roots (`:prompt-paths` / `:command-paths` / `:skill-paths`) so Isaac adapts to existing layouts.
- **Format:** markdown + EDN frontmatter (reuse cron's single-md-with-frontmatter pattern).
- **Disambiguation precedence:** `type:` > `user-invocable:` > directory/filename; warn on conflict; skip+warn on no signal. Skills accept `<name>/SKILL.md` and `<name>.md`.
- **Project-root detection:** walk up from cwd to `.isaac/` or a configured prompt dir; else cwd-is-root.
- **Resolution:** union across layers, most-specific wins (project > global). Frontmatter-index; bodies lazy.
- **Timing:** debug log (elapsed-ms + file/command/skill counts) on per-turn resolve, to inform a later session-cache decision.

## Scenarios (to draft)
Discovery of a command + a skill; type disambiguation (conflicting signals); project > global precedence; project-root walk-up; timing log emitted.

## Relationship
Parent: isaac-nwj3. Blocks the command-expansion bean and the deferred beans (skill activation, ACP advertisement, rules, crew-specific) which build on this registry.


## Skill file shapes (MVP)

Support **both** `<name>.md` (flat; name = file) and `<name>/SKILL.md` (dir; name = directory) — the latter for compatibility with existing Claude/agent-lib skills. **Bundled reference files in a skill directory are OUT OF SCOPE** — MVP inlines only the markdown body. Bundled-file support (entangles fs-bounds + how files reach the model) is a separate deferred bean.
