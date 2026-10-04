---
# isaac-np1m
title: 'Foreman action berth: modules contribute action types; built-ins register through it; unknown types are config errors'
status: in-progress
type: feature
priority: normal
created_at: 2026-10-04T19:16:25Z
updated_at: 2026-10-04T20:45:48Z
blocked_by:
    - isaac-7sfq
---

Likely repo: **isaac-foreman**. Micah + planner, 2026-10-04. Blocked by isaac-7sfq (actions write instance data).

## Why

Any kind of action should be pluggable into a state machine: Foreman core
ships :log, :turn, :notify (and :exec, isaac-c1hy); everything else — loading
a bean in-process, creating a worksite, posting somewhere — should be a module
contribution, not a Foreman change.

## Design

- New berth `:isaac.foreman/action`, keyed by action type keyword. Foreman's
  own types register through it as ordinary built-in contributions.
- An action is `(fn [ctx spec])`: ctx carries machine, instance id, the
  instance data, and the event; spec is the machine's action map. It returns
  nil, `{:data {...}}` (merged into the instance data before the next action
  renders), or `{:failed {...}}` (stops the list; fires `:<action>-failed`
  with that data under `:failed`, same shape and rules as isaac-c1hy).
- The machine schema's action `:type` validates against the registered types
  (`registered-in?` the berth); each contribution may contribute its own
  action-spec schema fragment.
- Machine tests (`foreman test`) still record actions without running them.
- New fixture step for the feature: `the "chime" action module is registered`
  (spec-provided module; `:chime` returns `{:data {:rang <:times>}}`).

## Acceptance

- isaac-foreman `features/foreman/action_berth.feature` — "a module-contributed action runs on a transition and writes the data"
- "an action type nobody registered is a config error" (already green: today's one-of schema) and the rest of the isaac-foreman features stay green.

feature-baseline: isaac-foreman 6decfc4fe4da4efb6e65dbeada57fd40490b2f75
feature-blob: isaac-foreman features/foreman/action_berth.feature 7822458fd6eda47ca858b13730ef26026c274ac5 19

## Work checkpoint (2026-10-04)

Done: action berth manifest, contributed chime fixture, action dispatch and schema validation; focused feature scenario passes. Next: fix legacy unit specs and complete bb ci, gate, land. Last run `bb ci` RED (15 failures, mostly unregistered :log/:exec in standalone core specs). Resume `src/isaac/foreman/action.clj:32`: lazy-register built-in berth entries for isolated core specs, then rerun bb ci.

## Landed on main (2026-10-04)

main-sha: isaac-foreman 6682a197e4753d76f9e34782bac86a895beeb7cf
