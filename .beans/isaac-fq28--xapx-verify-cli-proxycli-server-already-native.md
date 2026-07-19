---
# isaac-fq28
title: 'xapx: verify cli-proxy/cli-server already native'
status: in-progress
type: task
priority: low
tags:
    - unverified
created_at: 2026-07-19T17:10:52Z
updated_at: 2026-07-19T18:45:00Z
parent: isaac-xapx
---

Parent: isaac-xapx. (Independent — these already run native; not blocked by the re-home, but should end on the shared runner too.)

## Goal
Confirm **isaac-cli-proxy** and **isaac-cli-server** already run specs/features natively (their bb.edn comment: "Babashka runs specs/features directly"), and align them onto the shared `bb.test-tasks` runner if trivial.

## Acceptance
- [x] Both confirmed native + green (no `clojure -M` shell-out).
- [x] Either adopted the shared runner or documented why not.

## Results

### Confirmation (both already native)
Both repos hand-rolled native babashka invocation (`speclj/-main`, `gherclj/-main` directly in bb tasks) — **no `clojure -M` shell-out**. Spot-check green before conversion:
- **cli-proxy:** 11 specs, 7 features, 4 slow features — all 0 failures
- **cli-server:** 9 specs, 10 features — all 0 failures

### Adopted shared runner (trivial + consistent with xapx)

#### isaac-cli-proxy @ `ab3d16b`
- `bb.test-tasks` via foundation-test-support `@ 43cf46e`
- Foundation product pin aligned `7ed8e388` → `43cf46e` (cli_steps color API parity)
- Tasks: `spec` / `features` / `features-slow` / `ci` native; `jvm-spec` / `jvm-features` fallbacks
- Step globs: `isaac.**-steps` + `isaac.cli-proxy.feature-bootstrap`
- **bb ci green:** 11 + 7 + 4 examples, 0 failures (~9.4s with slow)

#### isaac-cli-server @ `6d13e48`
- `bb.test-tasks` via foundation-test-support `@ 43cf46e`
- Foundation product pin aligned `305c337` → `43cf46e`
- Tasks: `spec` / `features` / `ci` native; `jvm-spec` / `jvm-features` fallbacks
- Step globs: `isaac.**-steps` + `isaac.cli-server.feature-bootstrap`
- **bb ci green:** 9 + 10 examples, 0 failures (~1.8s)

### Why adopt (not document-only)
Hand-rolled native was already correct; switching to `tests/run-*` is trivial, gives timeout wrapper + subprocess-safe `run-ci!` pattern, and matches every other xapx consumer. No behavior change beyond pin alignment.
