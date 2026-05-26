---
# isaac-etpt
title: Skill bundled reference files (directory-packaged skills)
status: draft
type: feature
priority: normal
created_at: 2026-05-26T04:31:32Z
updated_at: 2026-05-26T04:31:32Z
parent: isaac-nwj3
blocked_by:
    - isaac-8qd5
---

Deferred from isaac-nwj3. Skills packaged as a **directory** (`<name>/SKILL.md` + bundled reference files: docs, scripts, templates the SKILL.md points at) — surface those bundled files to the model, not just the SKILL.md body.

## The hard part: fs-bounds
Bundled files live under `config/skills/<name>/`, which is **outside crew filesystem boundaries** (crew can't read `config/`). So "the model reads reference.md with fs tools" doesn't work without crossing that boundary. Options:
- **Inline** the referenced files alongside the SKILL.md body (no fs read; bloats the turn).
- A **controlled skill-resource mechanism/tool** that reads bundled files on the skill's behalf, scoped to skill assets only (bypasses general fs-bounds for this narrow case).

## MVP boundary
isaac-8qd5 discovers both file shapes but **inlines only the markdown body** — bundled files are ignored. This bean adds bundled-file support.

Parent: isaac-nwj3. Builds on the discovery/registry + command/skill inclusion.
