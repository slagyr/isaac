---
# isaac-1umd
title: 'Post-deploy: confirm stateful Responses chaining drops grok cycle-2+ body size (isaac-7l5m)'
status: completed
type: task
priority: normal
created_at: 2026-07-13T18:27:26Z
updated_at: 2026-09-19T18:09:21Z
---

## Goal

Confirm, one-time on zanebot after isaac-7l5m deploys, that stateful Responses
chaining actually drops cycle-2+ request body sizes on a real grok-composer work
turn.

## Why split from isaac-7l5m

isaac-7l5m landed the within-turn `previous_response_id` + `store:true` chaining
and proved it hermetically at wire-shape level
(`features/llm/api/responses/stateful.feature`). The remaining acceptance item
is a live post-deploy observation with no code dependence — it cannot gate the
merged, green code contract (same precedent as l70j->l7l4, k1po->6eo4,
la8h->exg7).

## Acceptance (one-time, post-deploy)

- [ ] After the `:isaac.agent` pin advances to the merged isaac-7l5m and zanebot
      is redeployed, run a real grok-composer work turn (a `:stateful true`
      provider/model) and inspect `server.log` `:llm/http-request :body-chars`.
- [ ] Cycle-1 body is full-context (~1MB scale as before); cycle-2+ bodies drop
      to KB-scale (only the new tool outputs + `previous_response_id`).
- [ ] chatgpt turns remain byte-identical (store:false, no chaining fields).
- [ ] Record the observed cold/warm body-char numbers in this bean. If chaining
      does NOT reduce body size on the live provider, record that finding — it
      is a provider/deploy observation, not an isaac-7l5m regression, and gets
      its own bean.

## Notes

- No production code expected here; this is an environment/observation check.
- Depends on isaac-7l5m merging first and the `:isaac.agent` registry pin
  advancing.

## Observation (planner, 2026-09-19 18:06Z, zanebot)

`models/grok-4-6.edn` gained `:stateful true` at 18:03Z (hot-reloaded; scrapper/verify/plan crews all use it). Diagnostic two-cycle turn on `isaac-work-3` (exec `date -u`), from `cli.log`:

| cycle | body-chars | body-keys |
|---|---|---|
| 1 | 958,425 | input instructions model reasoning store stream tools |
| 2 | 844,856 | input model **previous_response_id** reasoning store stream tools |

Chaining is active (xAI accepted `previous_response_id`, 200), but cycle 2 is 88% of cycle 1, not KB-scale. Cause: `isaac.llm.api.responses/->responses-request` builds the chained `:input` by filtering **every** tool-role / function_call_output message in the whole transcript (253 tool results, 871,655 chars on work-3), not only the outputs produced since the last response id. Filed as isaac-siua. Acceptance item 2 therefore fails for an isaac-agent reason, not a provider one; this observation bean is done.
