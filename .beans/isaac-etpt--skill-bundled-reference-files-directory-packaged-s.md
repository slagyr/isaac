---
# isaac-etpt
title: Skill bundled reference files (directory-packaged skills)
status: draft
type: feature
priority: normal
created_at: 2026-05-26T04:31:32Z
updated_at: 2026-05-26T13:04:45Z
parent: isaac-nwj3
blocked_by:
    - isaac-8qd5
    - isaac-bgx0
---

Deferred from isaac-nwj3. Skills packaged as a **directory** (`<name>/SKILL.md` + bundled reference files: docs, scripts, templates the SKILL.md points at) — surface those bundled files to the model, not just the SKILL.md body.

## The hard part: fs-bounds
Bundled files live under `config/skills/<name>/`, which is **outside crew filesystem boundaries** (crew can't read `config/`). So "the model reads reference.md with fs tools" doesn't work without crossing that boundary. Options:
- **Inline** the referenced files alongside the SKILL.md body (no fs read; bloats the turn).
- A **controlled skill-resource mechanism/tool** that reads bundled files on the skill's behalf, scoped to skill assets only (bypasses general fs-bounds for this narrow case).

## MVP boundary
isaac-8qd5 discovers both file shapes but **inlines only the markdown body** — bundled files are ignored. This bean adds bundled-file support.

Parent: isaac-nwj3. Builds on the discovery/registry + command/skill inclusion.


## Mechanism: extend load_skill with a resource arg (unifies with isaac-bgx0)

The scoped-tool option is realized as `load_skill(name, resource: "X")` — the same tool isaac-bgx0 introduces, extended to serve a skill's bundled files. This is Anthropic Agent Skills tier 3 (progressive disclosure: name+description -> body -> bundled files on demand), and the scoped reader resolves the fs-bounds tension (it reads the skill's own dir under config/ on the skill's behalf, without opening general crew fs access). Supersedes the earlier "inline-all vs new tool" fork.

Blocked by isaac-bgx0 (needs the load_skill tool).
