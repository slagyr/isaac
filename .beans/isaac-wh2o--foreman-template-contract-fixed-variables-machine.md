---
# isaac-wh2o
title: 'Foreman template contract: fixed variables (machine, instance, state, event, data), every action string templated, unknown variables rejected'
status: todo
type: feature
created_at: 2026-10-05T15:57:06Z
updated_at: 2026-10-05T15:57:06Z
blocked_by:
    - isaac-8379
---

Likely repo: **isaac-foreman**. Micah + planner, 2026-10-05. Blocked by isaac-8379 (shared engine).

## Why

Foreman templating grew field by field (prompt, then exec :command/:cwd, then
:frequencies in isaac-8r7m). It needs one rule and a defined set of variables.

## Design (agreed)

- Variables, fixed and documented: `{{machine}}`, `{{instance}}`, `{{state}}` (the
  state just entered), `{{event}}` (the event that fired the transition),
  `{{data.<path>}}` (the instance data).
- Rule: every string value in an action's spec is templated (prompt, frequencies,
  command, cwd, :log :message, resource-pool names); keywords and numbers are not.
  Rendered via `isaac.foundation.template/render-all` at enqueue/run time.
- `config validate` rejects any placeholder outside the set (`placeholders`), naming
  the machine and action. An absent `{{data.…}}` key at run time fills empty (data
  arrives over time) and is never a config error.
- The handbook chapter lists the variables and the rule.

## Acceptance

- isaac-foreman `features/foreman/template_contract.feature` — "state and event are template variables", "a log message reads the instance data like any other string", "an unknown template variable is a config error".
- "an absent data key is not a config error" (already green) and the rest of the isaac-foreman features stay green.
