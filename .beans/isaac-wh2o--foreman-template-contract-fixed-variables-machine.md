---
# isaac-wh2o
title: 'Foreman template contract: fixed variables (machine, instance, state, event, data), every action string templated, unknown variables rejected'
status: completed
type: feature
priority: normal
created_at: 2026-10-05T15:57:06Z
updated_at: 2026-10-05T22:36:38Z
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

feature-baseline: isaac-foreman fd10540e04757950e71bf67de21fa96606c72b53
feature-blob: isaac-foreman features/foreman/template_contract.feature d75ca4fcb63916ab14fdde9defd6716c51ca33a9 16
feature-blob: isaac-foreman features/foreman/template_contract.feature d75ca4fcb63916ab14fdde9defd6716c51ca33a9 29
feature-blob: isaac-foreman features/foreman/template_contract.feature d75ca4fcb63916ab14fdde9defd6716c51ca33a9 41

## Landed on main (2026-10-05)

main-sha: isaac-foreman e912e3c823084f217fb2a9ca6be9a67bef9e4f48

Foreman renders all action string leaves at firing time against the current instance data, entered state and triggering event; turn requests persist their rendered fields for retry. Config checks reject unknown variables in local and shared actions. The handbook documents the contract. `bb ci`: 101 specs, 52 features, zero failures; `bb bean-gate verify isaac-wh2o`: PASS on the main squash commit.
