---
# isaac-fq9c
title: 'Hail record: split delivery-bound session from addressing :session'
status: draft
type: task
created_at: 2026-07-03T18:07:50Z
updated_at: 2026-07-03T18:07:50Z
---

## Problem (2026-07-03, Micah review of a live CI hail)

Top-level :session on a hail record means two things depending on lifecycle stage: an explicit addressing input (d47fc31: explicit session trumps band selectors) AND the delivery-time binding stamped when reach-one picks a candidate. Reading a delivered record, you cannot tell which. Confusing for operators and for code.

## Design

- Addressing stays where addressing lives: explicit session targeting normalizes into :frequencies (alongside band/tags/crew/reach).
- The delivery-time binding gets its own field (e.g. :bound-session), stamped at bind. Metadata preamble Session line reads from it.
- Clean cutover: no dual-read of the old shape; migrate features/specs that assert :session on records to the right field.

## Likely repo scope

isaac-hail (queue/router/delivery_worker, features). Scenarios to be reviewed before promotion.
