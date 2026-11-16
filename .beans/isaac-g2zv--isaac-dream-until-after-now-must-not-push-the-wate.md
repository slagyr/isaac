---
# isaac-g2zv
title: 'isaac-dream: --until after now must not push the watermark into the future'
status: in-progress
type: bug
priority: normal
tags:
    - dream
created_at: 2026-11-16T14:49:07Z
updated_at: 2026-11-16T14:51:07Z
parent: isaac-pcm3
---

Repo: **isaac-dream** (`src/isaac/dream/run.clj`, `run!`). Found 2026-11-15: a manual `dream run --crew zane --since 2026-11-14 --until 2026-11-21` on zanebot moved Zane's watermark to 2026-11-21, so the nightly dream would have skipped five days of messages. Fixed by hand on zanebot (state.edn backed up).

## Fix

The window end is never later than the dream's start time: `until = min(--until, now)`. The diary's `window.until` and the watermark both use the capped value.

## Acceptance

`bb features features/dream/history.feature` green with the `@wip` tag removed from the scenario "an --until after now ends the watermark at now" (the last scenario in the file), plus `bb spec` and full `bb features` green. Bump the module version.

## Implementation

Capped `run!`'s window end to the earlier of `--until` and the dream start time; the diary and final watermark share that bound. Removed only the target scenario's `@wip` and bumped the module manifest to 0.2.2. The scenario failed before the fix (watermark 2026-04-30 instead of 2026-04-22). `bb features features/dream/history.feature`: 7 examples, 40 assertions; `bb ci`: 32 specs / 44 features green. `bb bean-gate verify isaac-g2zv --dir isaac-dream=../isaac-dream --ref isaac-dream=main`: PASS.

## Landed on main (2026-11-16)

main-sha: isaac-dream cbb5fc6b46c0ba4309d50e66ad7a6e47b7d079e2

feature-baseline: isaac-dream 83923e51b7f5114d36b7db84a1c6cf384d06d1c8
feature-blob: isaac-dream features/dream/history.feature eb6852536aa9a96ba1367b6d0a9bd4236fac03b6 166
