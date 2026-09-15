---
# isaac-efb5
title: 'Suite health (isaac-foundation): log viewer follow seek can skip an appended line'
status: todo
type: bug
priority: normal
tags:
    - suite-health
created_at: 2026-09-14T02:03:50Z
updated_at: 2026-09-15T20:21:41Z
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
