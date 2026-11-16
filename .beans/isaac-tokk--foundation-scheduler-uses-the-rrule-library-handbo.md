---
# isaac-tokk
title: Foundation scheduler uses the rrule library; handbook lists the accepted rules
status: draft
type: feature
priority: normal
created_at: 2026-11-16T23:15:56Z
updated_at: 2026-11-16T23:15:56Z
blocked_by:
    - isaac-w7rm
    - isaac-3s4i
---

Foundation's scheduler uses the rrule library instead of its own `scheduler/calendar.clj`, and agents can see which rules are accepted.

- Pin the library by git sha in foundation `deps.edn` and `bb.edn`; delete `scheduler/calendar.clj` and move its specs' coverage to the library.
- `features/scheduler/calendar.feature` keeps passing unchanged.
- Handbook: the cron chapter (isaac-cron) and foundation's scheduler section list the accepted RRULE parts, with examples (weekdays at 9, last Friday of the month, every 2 weeks with `:dtstart`).
- An error for an unsupported or invalid part names the part and points to the handbook section.

Acceptance: foundation features and specs green against the pinned library; a scenario shows a refused rule's error naming the part (scenario to be written before dispatch).
