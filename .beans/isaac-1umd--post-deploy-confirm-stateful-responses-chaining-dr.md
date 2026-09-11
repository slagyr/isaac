---
# isaac-1umd
title: 'Post-deploy: confirm stateful Responses chaining drops grok cycle-2+ body size (isaac-7l5m)'
status: todo
type: task
priority: normal
created_at: 2026-07-13T18:27:26Z
updated_at: 2026-07-13T18:27:26Z
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
