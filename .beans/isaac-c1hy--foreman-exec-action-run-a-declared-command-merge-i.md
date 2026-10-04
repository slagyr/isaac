---
# isaac-c1hy
title: 'Foreman :exec action: run a declared command, merge its output into instance data, fire <action>-failed on failure'
status: todo
type: feature
created_at: 2026-10-04T19:15:31Z
updated_at: 2026-10-04T19:15:31Z
blocked_by:
    - isaac-7sfq
---

Likely repo: **isaac-foreman**. Micah + planner, 2026-10-04. Blocked by isaac-7sfq (instance data).

## Why

The bean-work machine needs to load a bean's content (and later, set up a
worksite) before it prompts. Foreman's actions today are :log, :turn and
:notify only. Ruling (Micah, 2026-10-04): "no commands from config" applies
to the generic runtime (drive, hail delivery) and model agency (isaac-0uim),
not to Foreman, the orchestration layer; machine config is an admin surface,
so declared commands are allowed here.

## Design

- New action type `:exec`: `{:type :exec :command [...] :into :<key> :cwd "..." :timeout <s>}`.
- `:command` is an argv vector (no implicit shell; name `sh -c` explicitly if
  you want one). Each element is templated like prompts (`{{instance}}`,
  `{{machine}}`, `{{data.a.b}}`). `:cwd` is templated the same way.
- stdout lands in the instance data under `:into`: a JSON or EDN map/vector is
  parsed, anything else is kept as trimmed text. Without `:into`, output is
  discarded (log it).
- Actions run in list order; an exec action's data is merged before the next
  action renders, so `[:load-bean :work]` loads, then prompts.
- Non-zero exit or timeout (default 60s; the process is killed): the remaining
  actions in that list do not run, and Foreman fires `:<action>-failed` with
  data `{:exec {:action <name> :exit <n> :stderr <head> :timeout <bool>}}`
  (merged like any handled event's data). A machine without a row for it
  records it as unhandled.
- Add `:exec` to the action-type schema (`:command`, `:into`, `:cwd`,
  `:timeout`). Machine tests (`foreman test`) still only record actions, never
  run them.
- Logs `:foreman/exec` with action, argv, exit, ms.

## Acceptance

- isaac-foreman `features/foreman/exec_action.feature` — all four scenarios.
- The rest of the isaac-foreman features stay green.

feature-baseline: isaac-foreman 4ae8e8195e30b21fd1840e62450bf9c8d37a13b8
feature-blob: isaac-foreman features/foreman/exec_action.feature 6d391ae76e2eeb971d547e2ffce4dade7f22ab5c
