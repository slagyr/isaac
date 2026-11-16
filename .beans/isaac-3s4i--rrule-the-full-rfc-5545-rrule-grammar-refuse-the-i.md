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

EXDATE and RDATE (Micah, 2026-11-16: add them; people using the library will want them):
- EXDATE: date-times removed from the rule's set. RDATE: date-times added to it. RFC 5545 §3.8.5.1 and §3.8.5.2.
- The library takes them as inputs beside the rule and `:dtstart` (lists of local date-times, or iCalendar `EXDATE`/`RDATE` value strings), and the result is (rule run times + RDATE) - EXDATE, sorted, with no duplicates. COUNT limits the rule's own run times, before RDATE and EXDATE apply, as the RFC says.
- The ical4j comparison covers them.
- How cron jobs spell them (e.g. `except:` / `also:` keys) belongs to isaac-tokk.

Acceptance: the ical4j comparison spec generates over the full grammar and stays green; the invalid combinations each have a spec naming the error.
