---
# isaac-8r7m
title: Foreman templates :frequencies like prompts, so each instance can have its own session
status: in-progress
type: bug
priority: normal
created_at: 2026-10-05T15:45:45Z
updated_at: 2026-10-05T15:47:37Z
---

Likely repo: **isaac-foreman**. Found by the bean-work machine's own machine tests, 2026-10-05.

## Why

The bean-work machine gives each bean its own sessions:
`{:crew "scrapper" :session "bean-{{instance}}" :create :if-missing}` for the worker,
`{:crew "prowl" :session "plan-{{instance}}" …}` for alerts. Foreman templates only
`:prompt` (`core.clj` `fill-prompt`); `:frequencies` is passed through raw
(`core.clj:53`), so every instance would share one session literally named
`bean-{{instance}}`.

## Design

- String values in a `:turn` action's `:frequencies` (top level, and strings inside
  vectors such as `:session ["…"]` / tags) are templated exactly like `:prompt`:
  `{{machine}}`, `{{instance}}`, `{{data.a.b}}`; a missing key fills empty.
- Rendering happens where the prompt is rendered (at enqueue), so a pending/retried
  turn targets what was meant.
- The machine-test runner's `the "<action>" target is:` shows the rendered target.
- Non-string values (`:create :if-missing`, keywords) pass through unchanged.

## Acceptance

- isaac-foreman `features/foreman/templated_frequencies.feature` — both scenarios.
- The rest of the isaac-foreman features stay green.

feature-baseline: isaac-foreman 4c40afb670f605d20be629b0987486d825ef48da
feature-blob: isaac-foreman features/foreman/templated_frequencies.feature 778b73fb2d74272c7930036b1d90ccbc3078b5d5

## Landed on main (2026-10-05)

main-sha: isaac-foreman 17fa14a38eacdb8f9700203773291e121678ed77
