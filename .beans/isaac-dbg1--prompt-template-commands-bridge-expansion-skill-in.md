---
# isaac-dbg1
title: 'Prompt-template commands: bridge expansion + skill includes'
status: draft
type: feature
priority: normal
created_at: 2026-05-26T04:21:10Z
updated_at: 2026-05-26T04:21:10Z
parent: isaac-nwj3
blocked_by:
    - isaac-8qd5
---

MVP for isaac-nwj3; builds on the discovery bean. Config-defined commands whose body is a prompt template, expanded into the turn input at the bridge.

## Scope
- A command body = prompt **template** with `{{param}}` placeholders; frontmatter declares params + included skills.
- The bridge recognizes config-defined **prompt-template** commands (a second kind alongside builtin **handler** commands) and **expands them into the turn's user input** (vs replying).
- **Expansion:** substitute params; resolve declared skills -> load bodies -> **inline into the user turn**; the **expanded** prompt is stored/sent (not the raw `/cmd`).
- Works for any producer (CLI user, hail carrying `/work X`, cron) via the same bridge triage.

## Demonstrating deliverable
Author `config/commands/work.md` (+ `skills/tdd.md`, `skills/gherclj.md`, adapted from agent-lib) and confirm a hail carrying `/work <bean>` expands. (May be a small follow-up task.)

## Scenarios (to draft)
Command expands with param substitution; declared skills inlined into the user turn; expansion (not raw `/cmd`) persisted; unknown command passes through / errors.

## Relationship
Parent: isaac-nwj3. **Blocked by the discovery bean.**
