---
# isaac-bvzr
title: A scheduled dream sends its digest
status: completed
type: bug
priority: normal
created_at: 2026-11-18T17:49:18Z
updated_at: 2026-11-18T17:55:29Z
---

Repo: **isaac-dream** (`src/isaac/dream/run.clj` `schedule!`, `src/isaac/dream/cli.clj` `digest!`).

## What is wrong

A dream started by hand (`isaac dream run`) sends the operator a digest:
the new proposals with their `dream approve` commands, or a failure
notice. The nightly scheduled dream sends nothing. `schedule!`'s handler
calls `run!` with no `:notify!`; only the CLI passes `digest!`. So on
yopp, three habits sat proposed with no message to Micah, and on zanebot
two failed nights went unreported.

## Fix

The scheduled run sends the same digest as the CLI run, to the same
place: `crew.<id>.dream.digest`, else `attention.notify`. Move the digest
out of the CLI so both paths share it.

## Acceptance

- `features/dream/schedule.feature` "a scheduled dream sends its digest" passes.
- The existing digest scenarios in `features/dream/digest.feature` still pass.
- `bb ci` green.

feature-baseline: isaac-dream ee26ab56224c87833771ef500b9097fbcd482189
feature-blob: isaac-dream features/dream/schedule.feature a8eebf4eb2fbc5b1d43e7ebf54b96e14df4ce450 80

## Landed on main (2026-11-18)

Shared digest sender now serves CLI and scheduled runs. `bb ci`: 60 specs, 79 features, all passing; bean gate PASS on squash commit.

main-sha: isaac-dream 8ec4d5671248bdce23ebeb32a63730c6568fadb4
