---
# isaac-od6i
title: Drop hardcoded main crew fallbacks in isaac-episodes
status: todo
type: task
priority: normal
tags:
    - episodes
created_at: 2026-09-16T15:49:57Z
updated_at: 2026-09-23T21:26:15Z
blocking:
    - isaac-zule
blocked_by:
    - isaac-bfwn
---

isaac-episodes still falls back to a hardcoded `"main"` crew in policy, recall, migrate, cli, layout, and lifecycle. After defaults.crew is required (parent bean), those fallbacks must use `:defaults :crew` or the session’s `:crew`, never `"main"`.

## Decisions

- Decision (2026-09-16, Micah): same cutover as the agent bean — no production `"main"` crew identity.
- Blocked until the agent bean lands (schema + charge/session stamp).

## Scope (isaac-episodes)

Replace `(or crew "main")` / `(or (:crew session) "main")` / `resolve-crew-context cfg "main"` with session crew or `(get-in cfg [:defaults :crew])`.

## Scenarios

No new Gherkin in this repo. Parent bean owns the user-visible contract. This bean is grep + specs.

## Acceptance

```
cd isaac-episodes
bb spec
bb ci
```

One-time: `git grep -n '"main"' -- src` has no crew-identity fallback.
