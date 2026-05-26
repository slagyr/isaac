---
# isaac-ypy5
title: 'Rules: always-on project-scoped system-prompt instructions (new prepared-prompt type)'
status: in-progress
type: feature
priority: normal
created_at: 2026-05-26T04:21:33Z
updated_at: 2026-05-26T14:44:47Z
parent: isaac-nwj3
blocked_by:
    - isaac-8qd5
---

Deferred from isaac-nwj3. A `rules` prepared-prompt type: always-on, project-scoped instructions (CLAUDE.md / .cursorrules / AGENTS.md style).

Distinct from commands (invoked) and skills (included on demand): rules are **always on** -> land in the **system prompt** (stable per project -> cached, like a project-level soul addition). Touches the injection-guard/soul composition (isaac-uysx). Likely shares a "project context block" slot with the skill-menu advertisement.

A new `type:` value on the same discovery machinery.

Parent: isaac-nwj3.


## Feature file

`features/prompts/rules.feature` — 3 `@wip` scenarios: rule body always in the cached system prompt; global + project rules both apply (additive); stable sorted order (cache-safe). Run:

```
bb features features/prompts/rules.feature
```

**Definition of done:** remove `@wip` and green.

## Mechanism note

A rule = a prepared prompt with **always-on activation** (full body unconditionally in the cached system prompt), vs a skill (description in system + body via `load_skill` on demand). Same fragment machinery, different activation — but rules earn a distinct type for **guaranteed presence** (safety/conventions you cannot leave to the model to load). Built on the system-prompt composition from isaac-bgx0 / isaac-uysx.
