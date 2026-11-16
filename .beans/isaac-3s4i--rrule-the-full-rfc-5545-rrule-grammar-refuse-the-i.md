---
# isaac-3s4i
title: 'rrule: the full RFC 5545 RRULE grammar; refuse the invalid combinations'
status: draft
type: feature
priority: normal
created_at: 2026-11-16T23:15:56Z
updated_at: 2026-11-16T23:15:56Z
blocked_by:
    - isaac-w7rm
---

Complete the RRULE grammar in the rrule library, so an agent's rule either runs exactly as RFC 5545 §3.3.10 says or is refused.

Add:
- BYSETPOS, BYYEARDAY, BYWEEKNO, WKST
- negative BYMONTHDAY (e.g. -1 = last day of the month)
- the RFC's expand-and-limit table for each BYxxx under each FREQ
- refusing the combinations the RFC calls invalid: a numbered BYDAY (1MO) outside MONTHLY/YEARLY, or a numbered BYDAY with BYWEEKNO under YEARLY; BYWEEKNO outside YEARLY; BYYEARDAY under DAILY/WEEKLY/MONTHLY; BYMONTHDAY under WEEKLY

Still refused: BYSECOND (jobs run on whole minutes) and FREQ=SECONDLY.

Not in RRULE, decide with Micah first: EXDATE/RDATE would be separate job keys, not rule parts.

Acceptance: the ical4j comparison spec generates over the full grammar and stays green; the invalid combinations each have a spec naming the error.
