---
# isaac-jx7u
title: 'Dead-letter resurrection: loud comm post + isaac hail requeue'
status: draft
type: feature
priority: normal
created_at: 2026-07-08T22:13:07Z
updated_at: 2026-07-08T22:13:07Z
---

## Goal

When a hail does dead-letter (real poison budget exhaustion), two things follow: it makes noise where humans look, and it can be resurrected with one command after the underlying problem is fixed — no hand-crafting replacement hails.

## Observed (2026-07-08)

The o14c handback dead-letter was invisible in discord (thread ended at "handed back to worker"); the board just went quiet. Recovery meant manually composing a fresh band hail and restating the context.

## Design

- **`:hail/dead-lettered` posts to notification-comm**: bean-id, thread, attempts, error keyword — the at-a-glance channel shows the death, not just server.log. (Undeliverables stay WARN-log-only per isaac-axzg descope; dead-letter is terminal failure of claimed work — higher severity.)
- **`isaac hail requeue <id>`**: moves `failed/<id>.edn` back to pending with attempts reset to 0, original prompt/params/thread intact, provenance stamped (`:requeued-from`, `:requeued-at`). Works for any failed record.
- Consider: a requeue-all-by-reason flag for mass weather recoveries (e.g. everything dead-lettered as `:api-error` during an outage window).

## Scenarios (spec with Micah before dispatch)

Coverage to spec: dead-letter emits the comm post with bean-id and error; requeue restores a failed record to pending with attempts 0 and provenance; requeued delivery binds and delivers normally; requeue of a nonexistent id errors cleanly.
