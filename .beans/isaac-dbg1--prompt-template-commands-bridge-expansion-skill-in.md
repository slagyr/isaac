---
# isaac-dbg1
title: 'Prompt-template commands: bridge expansion + skill includes'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-05-26T04:21:10Z
updated_at: 2026-05-26T17:45:44Z
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


## Feature file

`features/prompts/commands.feature` — 4 scenarios: expand + param substitution; declared skills inlined into the user turn; unknown command from an interactive caller -> rejected; unknown command in a hail -> dispatched raw and delivered. Run:

```
bb features features/prompts/commands.feature
```

**Definition of done:** remove `@wip` and green.

## Scope additions

- Bridge recognizes config-defined **prompt-template** commands (new kind alongside builtin **handler** commands); expansion = `{{param}}` substitution + inline declared skill bodies into the **user turn**; the **expansion** is stored/sent (not the raw `/cmd`).
- **Origin-aware unknown-command handling:** interactive caller -> "unknown command" reply, no turn; autonomous (hail/cron) -> fall through, dispatch the raw input, deliver (never drop/dead-letter).
- Producer-agnostic via the same bridge triage (CLI, hail, cron).
- New steps: none (reuses user-sends, hail-delivery-worker-ticks, transcript-matching, reply-contains).
