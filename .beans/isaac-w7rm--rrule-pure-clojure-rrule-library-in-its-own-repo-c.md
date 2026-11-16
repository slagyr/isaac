---
# isaac-w7rm
title: 'rrule: pure Clojure RRULE library in its own repo, checked against ical4j'
status: draft
type: feature
priority: normal
created_at: 2026-11-16T23:15:42Z
updated_at: 2026-11-16T23:15:42Z
---

A pure Clojure RRULE library in its own repo, checked against ical4j. Micah, 2026-11-16: "bean it as its own library".

Why: foundation's scheduler parses a subset of RRULE (`isaac-foundation/src/isaac/foundation/scheduler/calendar.clj`, from isaac-ovas). It refuses anything outside the subset, but nothing checks that the subset's results match the spec. Micah: custom is fine "as long as we can parse accurately and efficiently".

Scope:
- New repo `slagyr/rrule` (name to confirm with Micah before creating). Plain Clojure on `java.time`. No runtime dependencies. Runs in bb and the JVM.
- Move `calendar.clj` in as the starting point, same behavior: `parse-rule` and `next-fire-at`, the same errors, seconds fixed at 0.
- ical4j as a test-only JVM dependency. A spec generates random rules in the supported subset (fixed seed, at least 1,000 rules) and checks the next 50 run times from several start times and zones, DST changes included, against ical4j's `Recur`. Any mismatch prints the rule.
- bb and JVM spec runs, plus CI.

Out of scope: new RRULE parts (next bean), foundation switching to the library (later bean).

Acceptance: specs green in bb and on the JVM; the ical4j comparison is green over the current subset, or each mismatch is filed as a bug.
