---
# isaac-yxuy
title: 'isaac-agent: finish the fixture name scrub — spec and handbook'
status: todo
type: task
priority: critical
created_at: 2026-10-09T17:55:53Z
updated_at: 2026-10-09T17:55:53Z
---

URGENT (Micah, 2026-10-09). Finish replacing a real deployment's crew name in `isaac-agent` examples with the Marigold cast. Mechanical; no behavior change. The planner has already changed the feature files on main.

## What is left

The same sample crew id, a four-letter name, still appears in two places:

- `spec/isaac/agent/session/default_crew_steps_spec.clj`, lines 17–24: the crew key, the `:defaults :frequencies :crew` value, and the soul text "You are ….".
- `resources/isaac/agent/handbook.md`, about line 385: the `{:kind :crew :id "…"}` example.

Replace the id with `bartholomew` and the capitalized name with `Bartholomew`, matching `features/bridge/unknown_crew.feature`, where the planner made the same change and marked the scenario `@wip`.

## Acceptance

Run from `isaac-agent`, with `@wip` removed from the scenario:

- `bb features features/bridge/unknown_crew.feature`
- `bb spec spec/isaac/agent/session/default_crew_steps_spec.clj`
- `git grep -i` for the old name prints nothing in the repo.
- `bb verify` and `bb jvm-spec` green.
- No pin, dependency or behavior change.

## Likely repo scope

`isaac-agent`.

feature-baseline: isaac-agent cec84c824b54da5c93a5513b12ebeb80d8b23dcb
feature-blob: isaac-agent features/bridge/unknown_crew.feature 570745b0c60c6d6224bc46b7519ada2ab89cc126 59
