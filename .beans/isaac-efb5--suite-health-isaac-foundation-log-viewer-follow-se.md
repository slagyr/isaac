---
# isaac-efb5
title: 'Suite health (isaac-foundation): log viewer follow seek can skip an appended line'
status: todo
type: bug
priority: normal
tags:
    - suite-health
created_at: 2026-09-14T02:03:50Z
updated_at: 2026-09-14T02:03:50Z
---

## Problem

`spec/isaac/log_viewer_spec.clj:344` intermittently fails: “does not skip a line appended between the initial dump and follow seek.” The failure reproduced on the oc3f land (`7a33619`/`8b4a33b`) and again around the isaac-8got landing, so it is not caused by the HTTP rename.

## Acceptance

- Reproduce or deterministically model the append-between-dump-and-seek race.
- Fix the log viewer follow transition so the intervening line is emitted exactly once.
- Add a stable regression spec.
- `bb ci` passes repeatedly without the line-344 flake.
