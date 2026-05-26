---
# isaac-8qd5
title: Prepared-prompt discovery & registry (commands + skills)
status: in-progress
type: feature
priority: normal
created_at: 2026-05-26T04:21:10Z
updated_at: 2026-05-26T13:21:36Z
parent: isaac-nwj3
blocked_by:
    - isaac-udzf
---

MVP foundation for isaac-nwj3. Discover commands/skills from layered roots and resolve a per-turn catalog.

## Scope
- **Roots:** install global `~/.isaac/config/{commands,skills}/` loaded once at startup; **project** `<root>/.isaac/{commands,skills}/` resolved **per turn**; configurable extra roots (`:prompt-paths` / `:command-paths` / `:skill-paths`) so Isaac adapts to existing layouts.
- **Format:** markdown + **YAML** frontmatter (per isaac-udzf, the md-frontmatter switch). YAML makes foreign (Claude/agent-lib) files readable natively.
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


## Disambiguation is full 3-signal in MVP (YAML makes it viable)

Because frontmatter is YAML (the common format), foreign files read natively, so the full ladder is MVP-viable: `type:` > `user-invocable:` (true -> command / false -> skill) > directory/filename. No separate foreign-format-ingestion deferral needed; only directory-convention differences remain (handled by configurable roots).


## Feature file

`features/prompts/catalog.feature` — 7 `@wip` scenarios: command discovered; skill via SKILL.md; type beats misfiled dir (+ warn); user-invocable in a generic root; directory fallback; project shadows global; project-root walk-up. Run:

```
bb features features/prompts/catalog.feature
```

**Definition of done:** remove `@wip` and green. Acceptance also requires the **debug timing log** on resolve (`elapsed-ms` + command/skill counts) — verified by enabling debug, not a scenario (debug is not spec-asserted).

**New steps:** `the prompt catalog is resolved`, `the prompt catalog for session "<key>" is resolved`, `the prompt catalog contains:`.


## Configurable dir-name -> type mapping (`prompt-dir-names`)

The path-disambiguation rung is **configurable**, not hardcoded: `prompt-dir-names` maps directory-segment names to types (default `{commands: command, skills: skill}`, **merged** with project overrides), so layouts using other dir names (e.g. `abilities/`) still resolve. Drives which subdirs of a base root are treated as typed prompt dirs. (Covered by the catalog.feature `prompt-dir-names` scenario.)
