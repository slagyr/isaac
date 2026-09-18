---
# isaac-efb5
title: 'Suite health (isaac-foundation): log viewer follow seek can skip an appended line'
status: in-progress
type: bug
priority: normal
tags:
    - suite-health
created_at: 2026-09-14T02:03:50Z
updated_at: 2026-09-18T18:24:56Z
---

## Problem

`spec/isaac/log_viewer_spec.clj:344` intermittently fails: “does not skip a line appended between the initial dump and follow seek.” The failure reproduced on the oc3f land (`7a33619`/`8b4a33b`) and again around the isaac-8got landing, so it is not caused by the HTTP rename.

## Acceptance

- Reproduce or deterministically model the append-between-dump-and-seek race.
- Fix the log viewer follow transition so the intervening line is emitted exactly once.
- Add a stable regression spec.
- `bb ci` passes repeatedly without the line-344 flake.



## Reproduction (2026-09-15, perceptor@isaac-verify)

CI Tests run 35018494124 on isaac-foundation main `c89964d` (isaac-f21o squash) failed this spec in the full `bb spec` suite. Isolated `bb spec spec/isaac/log_viewer_spec.clj` on that tree failed 5/5 (timeout ~10s at line 344). Next main commit `b374929` CI was green — still a flake, now easy to hit in isolation.



## Reproduction (2026-09-15, perceptor@isaac-verify-2)

CI Tests run 35022394582 on isaac-foundation main `e664914` (isaac-kbs5 squash) failed this spec in the full `bb spec` suite (1015 examples, 1 failure). Isolated `bb spec spec/isaac/log_viewer_spec.clj` on that tree failed 1/1 (timeout ~10s at line 344). Prior main `a0a2b0f` CI was green — still a flake. kbs5 did not touch log_viewer.



## Reproduction (2026-09-16, perceptor@isaac-verify)

CI Tests run 35125878258 on isaac-foundation main `e25b256` (isaac-1hs0 squash) failed this spec in the full `bb spec` suite (1031 examples, 1 failure) at `spec/isaac/log_viewer_spec.clj:376` (same `it`, line shifted by 1hs0 viewer-filter specs). Isolated `bb spec spec/isaac/log_viewer_spec.clj` on that tree failed 2/3 (~10s timeout). 1hs0 did change `log_viewer.clj` (level filter on `print-line!` / `read-initial-lines`) but the follow-seek race is the existing isaac-efb5 flake, not a 1hs0 regression. Do not reopen isaac-1hs0.


## Still flaky (planner, 2026-09-18) — promote

After 09-17's 'Make log follow race spec deterministic' (f9ae3fd) the spec still fails ~40% locally: 8 runs of `bb spec spec/isaac/log_viewer_spec.clj` → 3 failures, all 'tail! does not skip a line appended between the initial dump and follow seek', now as `Expected: true got: :isaac.log-viewer-spec/timeout` at :380. The deterministic rewrite moved the failure from a skipped line to a timeout — the race is in the product's dump→follow transition, not the spec. Acceptance unchanged; add: 20 consecutive green runs of that file.
