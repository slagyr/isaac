---
# isaac-jx7u
title: 'Dead-letter resurrection: loud comm post + isaac hail requeue'
status: todo
type: feature
priority: normal
created_at: 2026-07-08T22:13:07Z
updated_at: 2026-07-08T23:18:26Z
---

## Goal

When a hail does dead-letter (real poison budget exhaustion), two things follow: it makes noise where humans look, and it can be resurrected with one command after the underlying problem is fixed — no hand-crafting replacement hails.

## Observed (2026-07-08)

The o14c handback dead-letter was invisible in discord (thread ended at "handed back to worker"); the board just went quiet. Recovery meant manually composing a fresh band hail and restating the context.

## Design

- **`:hail/dead-lettered` posts attention**: bean-id, thread, attempts, error keyword — the at-a-glance channel shows the death, not just server.log. WORK-scoped event routing: the delivery's band `:notification-comm` (data block) first, falling back to the `:attention :notify` config (isaac-5a4n), else WARN log only. Posting = enqueue in `comm/delivery/pending/` (durable outbox). Unthrottled — deaths are individually newsworthy. (Undeliverables stay WARN-log-only per isaac-axzg descope; dead-letter is terminal failure of claimed work — higher severity.)

**Blocked by isaac-5a4n**: shares the `:attention` config and the outbox-assertion steps.

## Approved scenario A (Micah, 2026-07-08)

Genuine failure (grover `error` type) on attempt 5 with `data
{:notification-comm {:id "discord" :channel "boiler-room"}}` on the delivery:
failed/hail-1.edn contains attempts 5 + error :llm-error (cehc detail), and:
```
Then the directory "comm/delivery/pending" has exactly 1 file
And the only file in "comm/delivery/pending" EDN contains:
  | path    | value                                  |
  | comm    | discord                                |
  | target  | boiler-room                            |
  | content | contains "isaac-zzz" and "dead-letter" |
```
(bean-id from params, front and center). Fallback-to-`:attention` variant may
be added by the worker cheaply — same shape, config row instead of data block.
- **`isaac hail requeue <id>`**: moves `failed/<id>.edn` back to pending with attempts reset to 0, original prompt/params/thread intact, provenance stamped (`:requeued-from`, `:requeued-at`). Works for any failed record.
- Consider: a requeue-all-by-reason flag for mass weather recoveries (e.g. everything dead-lettered as `:api-error` during an outage window).

## Approved scenarios B + C (Micah, 2026-07-08)

**B — requeue resurrects and redelivers**: fixture plants `hail/failed/hail-1.edn`
(attempts 5, error :api-error, original prompt/crew/bound-session). Then:

```gherkin
When isaac is run with "hail requeue hail-1"
Then the exit code is 0
And the isaac file "hail/failed/hail-1.edn" does not exist
And the isaac file "hail/deliveries/hail-1.edn" EDN contains:
  | path           | value          | #comment                     |
  | id             | hail-1         |                              |
  | attempts       | 0              | fresh budget                 |
  | prompt         | Seal the leak. | original content intact      |
  | requeued-error | :api-error     | provenance: why it died      |
  | requeued-at    | #".+"          | provenance: when resurrected |
When the hail delivery worker ticks at "2026-04-21T10:00:00Z"
And the turn ends on session "engine-room"
Then the isaac file "hail/delivered/hail-1.edn" EDN contains:
  | path | value  |
  | id   | hail-1 |
```

Design: record returns to `deliveries/` (it IS a delivery; worker re-binds
normally, no band re-route); `:error` cleared but preserved as
`:requeued-error`; the tick→delivered leg proves the record is live, not just
relocated. Queue a grover `text` success for the redelivery.

**C — unknown id fails cleanly**:

```gherkin
When isaac is run with "hail requeue nope99"
Then the stderr contains "nope99"
And the exit code is 1
```

All CLI steps existing (bands.feature machinery); the command itself is the feature.
