---
# isaac-e0qe
title: Cron jobs use iCalendar RRULE; finite jobs leave the schedule
status: draft
type: epic
priority: normal
tags:
    - cron
    - scheduler
created_at: 2026-11-16T17:49:58Z
updated_at: 2026-11-16T17:49:58Z
---

Likely repos: **isaac-foundation** (RRULE parse + next-fire-at + scheduler trigger) then **isaac-cron** (job schema, register, auto-unset, handbook, feature rewrite). Dream keeps 5-field cron until a follow-up.

Parent design bean. The work is split into two children (2026-11-16): foundation (RRULE subset, next fire, `:calendar` trigger) and cron (schema cutover, finite-job removal, migration, `isaac cron list`). Each child carries its own `@wip` scenarios.

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

## Review decisions (2026-11-16, Micah approved the planner's review)

1. **Pure Clojure, bb + JVM.** The RRULE parser and next-fire iterator run under babashka (CLI, `isaac config validate`) and the JVM server alike. No Java calendar library (ical4j won't load in bb). This is a requirement, not an implementation choice.
2. **Anchoring.** Seconds are always 0; a rule without `BYMINUTE` fires at minute 0 (and without `BYHOUR`, per its `FREQ`). `INTERVAL > 1` and `COUNT` **require** `:dtstart` (config rejects them without it) — that is what pins the phase and the count.
3. **COUNT counts occurrences from `:dtstart`**, including any missed while the server was down. Stateless; survives restarts.
4. **Time zone and DST.** `UNTIL` and `:at` / `:dtstart` are local times in the install `:tz` (a trailing `Z` means UTC). On spring-forward, a nonexistent local time is skipped (RFC 5545); on fall-back, an ambiguous time fires once.
5. **A bad rule fails its own entity, not the config.** An invalid `:when` is a validation error naming `cron.<name>` and the bad part, like any other entity error; other jobs and the server keep running.
6. **Finite-job removal keeps a record.** When a one-shot or COUNT/UNTIL job is removed (mutate path), its last definition and last outcome stay in `<root>/cron.edn` history. Note: if a host's config dir is under git, the removal is an uncommitted change.
7. **Late one-shots have a window.** A missed `:at` fires late on return only within `:late-window` (default 12 h); past it, it is skipped, removed, and logged (`:cron/one-shot-expired`).
8. **`isaac cron list`** shows each job's rule (or `:at`) and its next fire time — the way to check a rule says what you meant.
9. **Cutover runs with the restart.** `:expr` hard-rejects, so each host's cron files are rewritten in the same breath as the restart that loads the new code (as with config-key renames). zanebot at deploy; yopp whenever it next upgrades (list its jobs in the cron bean). Corrected migrations:
   - `0 9 * * *` → `FREQ=DAILY;BYHOUR=9;BYMINUTE=0`
   - weekdays at 9 → `FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;BYHOUR=9;BYMINUTE=0`
   - `0 7-21 * * *` → `FREQ=DAILY;BYHOUR=7,8,9,10,11,12,13,14,15,16,17,18,19,20,21;BYMINUTE=0`
   - `*/30 * * * *` → `FREQ=HOURLY;BYMINUTE=0,30` (not `MINUTELY;INTERVAL=30`, whose phase depends on `:dtstart`)

These supersede the "Recommended (not signed off)" section above where they differ.
