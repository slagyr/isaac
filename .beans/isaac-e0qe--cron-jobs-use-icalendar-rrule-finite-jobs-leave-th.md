---
# isaac-e0qe
title: Cron jobs use iCalendar RRULE; finite jobs leave the schedule
status: draft
type: feature
priority: normal
tags:
    - cron
    - scheduler
created_at: 2026-11-16T17:49:58Z
updated_at: 2026-11-16T17:49:58Z
---

Likely repos: **isaac-foundation** (RRULE parse + next-fire-at + scheduler trigger) then **isaac-cron** (job schema, register, auto-unset, handbook, feature rewrite). Dream keeps 5-field cron until a follow-up.

Draft until a scenario plan is approved. Do not promote to todo without `@wip` scenarios.

## Why

Cron jobs are 5-field `:expr` (`minute hour day month weekday`) stored as config. That language cannot say “every other Friday” or “the first Tuesday of the month.” A one-shot faked as `0 9 16 11 *` is yearly, and the job file stays forever: isaac-cron never unsets config after a fire (only `<root>/cron.edn` last-run/last-status). Foundation already drops in-memory `:at` / `:delay` tasks after they fire; cron never uses those triggers.

## Design (2026-11-16, Micah)

**Stored language is iCalendar RRULE (RFC 5545), not a custom phrase dialect.** `config get` shows the rule. Any later friendly writer compiles *to* RRULE.

Replace isaac-cron `:expr` with:

| Field | Role |
|---|---|
| `:when` | RRULE value (the `RRULE:` body, not a full VEVENT) |
| `:at` | one-shot ISO datetime; no `:when` |
| `:dtstart` | optional with `:when`; pins INTERVAL phase (which Friday is week 0) |

`:when` and `:at` are mutually exclusive. Zone is the install `:tz`. Time of day lives in the rule (`BYHOUR` / `BYMINUTE`) or on `:at` / `:dtstart`.

Examples:

```yaml
when: "FREQ=WEEKLY;BYDAY=TU,TH;BYHOUR=9;BYMINUTE=0"
```

```yaml
when: "FREQ=WEEKLY;INTERVAL=2;BYDAY=FR;BYHOUR=9;BYMINUTE=0"
dtstart: 2026-11-21T09:00:00
```

```yaml
when: "FREQ=MONTHLY;BYDAY=1TU;BYHOUR=9;BYMINUTE=0"
```

```yaml
at: 2026-11-20T09:00:00
```

**Accepted subset** (anything else fails config load):

- `FREQ`: `MINUTELY`, `HOURLY`, `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`
- `INTERVAL`
- `BYDAY` including nth (`1TU`, `-1FR`)
- `BYMONTHDAY`, `BYMONTH`, `BYHOUR`, `BYMINUTE`
- `COUNT`, `UNTIL`
- `DTSTART` as the sibling `:dtstart` field (not stuffed into `:when`)

**Clean cutover.** `:expr` is removed. Unknown-key / missing `:when`-or-`:at` hard-reject. No cron-expr alias. Existing feature tables that set `cron.*.expr` are rewritten. Live hosts migrate after land (Zanebot: `0 9 * * *` → `FREQ=DAILY;BYHOUR=9;BYMINUTE=0`, weekdays `BYDAY=MO,TU,WE,TH,FR`, `0 7-21 * * *` → `FREQ=HOURLY;BYHOUR=7,8,9,10,11,12,13,14,15,16,17,18,19,20,21`, `*/30 * * * *` → `FREQ=MINUTELY;INTERVAL=30`).

**Finite jobs leave the schedule.** After a one-shot `:at` or a `COUNT`/`UNTIL` rule has no next fire, unset the config entity through the mutate path (`config unset cron.<name>`). Recurring rules with no limit stay. A past `:at` that remains in config would re-fire on the next tick after restart (foundation `:at` semantics) — cleanup is load-bearing.

Foundation grows a calendar trigger (`:kind :calendar` with the parsed rule) beside existing `:cron`. isaac-cron registers calendar tasks. `:kind :cron` stays for dream.schedule until a later bean.

Internals (library vs our own iterator) are an implementation choice. The file format is RRULE either way.

Handbook: isaac-cron chapter (`:when` / `:at` / `:dtstart`, subset, unset-on-exhaust, how to verify). Foundation scheduler section names the new trigger kind. Schema `:description`s feed the generated config reference.

## Recommended (not signed off)

- Missed one-shot: fire late when the server returns (unlike recurring `:cron/missed-schedule` skip).
- Failed turn: scheduler `:on-error :retry`, then unset and keep `last-error` in `cron.edn`. Success-only unset plus a past `:at` retries every tick.
- Auto-unset via mutate is a config change (the job’s intent includes expiry), not a raw file delete.

## Out of scope

- Custom English phrases (“every other Friday at 09:00”) as a stored language
- Full RFC 5545 (`BYSETPOS`, `BYYEARDAY`, `BYWEEKNO`, `SECONDLY`, `WKST` unless a real job needs it)
- Dream `crew.*.dream.schedule` still 5-field cron
- Quartz cron (`L`, `#` as cron fields, 6/7-field expr)

## Split if the scenario plan is large

1. Foundation: parse subset, next/previous fire, `:calendar` trigger, handbook scheduler blurb
2. isaac-cron: schema cutover, register, auto-unset, features, handbook, pin foundation
