---
# isaac-ovas
title: RRULE calendar trigger in the foundation scheduler (pure Clojure, bb + JVM)
status: in-progress
type: feature
priority: normal
tags:
    - scheduler
created_at: 2026-11-16T19:01:51Z
updated_at: 2026-11-16T19:37:55Z
parent: isaac-e0qe
---

Repo: **isaac-foundation**. Child 1 of isaac-e0qe (design + review decisions live there). Unblocks the cron child.

## What

A `:calendar` scheduler trigger beside `:interval` / `:delay` / `:at` / `:cron`: `{:kind :calendar :rule "<RRULE body>" :dtstart "<local datetime>" :zone "<IANA>"}`. Pure-Clojure RRULE subset parser and next-fire iterator that runs under babashka and the JVM (no Java calendar library). Accepted subset, anchoring, COUNT-from-:dtstart, UNTIL in zone, DST skip/once, and refusals exactly as in isaac-e0qe "Review decisions" 1–4. A finite rule leaves the scheduler after its last fire, as `:at` does. Expose next fire so `isaac cron list` can show it (the task's `:next-fire-at`). Trigger schema `:description`s; handbook scheduler section names the new kind.

## Acceptance

Scenarios in `isaac-foundation/features/scheduler/calendar.feature` (feature-level `@wip`; remove it when all pass):

| line | scenario |
|---|---|
| 16 | a weekly rule fires on each listed day at its hour |
| 28 | a rule without BYMINUTE fires at minute 0 |
| 40 | every other Friday follows the phase :dtstart sets (next-fire-at) |
| 56 | the first Tuesday of the month |
| 65 | COUNT counts from :dtstart, missed occurrences included, then leaves the schedule |
| 78 | UNTIL is a local time in the zone; the rule leaves the schedule after it |
| 88 | a local time skipped by spring forward does not fire that day |
| 98 | a local time repeated by fall back fires once |
| 108 | INTERVAL > 1 without :dtstart is refused |
| 115 | COUNT without :dtstart is refused |
| 122 | a part outside the subset is refused by name (BYSETPOS) |

Run: `bb features features/scheduler/calendar.feature`. Also `bb spec`, `bb jvm-spec` (bb/JVM parity is a requirement), full `bb features` green. Do not bump the module version.

feature-baseline: isaac-foundation e04b7b18f0fbbebfb378ca19ed3b120f04f74715
feature-blob: isaac-foundation features/scheduler/calendar.feature d9217ede531baded1388a1c0d51a2a7ade24779f 16,28,40,56,65,78,88,98,108,115,122
