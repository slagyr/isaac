---
# isaac-fq9c
title: 'Hail record: split delivery-bound session from addressing :session'
status: completed
type: task
priority: normal
created_at: 2026-07-03T18:07:50Z
updated_at: 2026-07-03T19:53:07Z
---

## Problem (2026-07-03, Micah review of a live CI hail)

Top-level :session on a hail record means two things depending on lifecycle stage: an explicit addressing input (d47fc31: explicit session trumps band selectors) AND the delivery-time binding stamped when reach-one picks a candidate. Reading a delivered record, you cannot tell which. Confusing for operators and for code.

## Design

- Addressing stays where addressing lives: explicit session targeting normalizes into :frequencies (alongside band/tags/crew/reach).
- The delivery-time binding gets its own field (e.g. :bound-session), stamped at bind. Metadata preamble Session line reads from it.
- Clean cutover: no dual-read of the old shape; migrate features/specs that assert :session on records to the right field.

## Scope narrowed (2026-07-03 investigation)

Addressing ALREADY normalizes into :frequencies (existing feature asserts frequencies.session on stored records). This bean is purely: rename the resolved-target field on delivery records `:session` -> `:bound-session`, update readers (delivery_worker, router), rename the field across feature fixtures/assertions.

## Migration map (rename in place with the implementation — not @wip duplicates)

- features/delivery.feature — ~9 rows (Given fixtures + Then assertions)
- features/explicit-session-routing.feature — 2 Then rows
- features/commands.feature — 1 Given row
- features/hail-metadata.feature — delivery Given fixtures

## Acceptance scenario (committed @wip, 2026-07-03)

isaac-hail features/delivery.feature — "binding stamps bound-session on the delivery record". Zero new steps.

Acceptance: un-@wip; migration map executed; `bb spec` / `bb features` green in isaac-hail.

## Likely repo scope

isaac-hail (queue/router/delivery_worker, features).
