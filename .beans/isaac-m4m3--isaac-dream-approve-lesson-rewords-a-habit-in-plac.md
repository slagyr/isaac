---
# isaac-m4m3
title: 'isaac-dream: approve --lesson rewords a habit in place instead of creating a superseding habit'
status: completed
type: bug
priority: normal
tags:
    - dream
created_at: 2026-11-16T17:17:38Z
updated_at: 2026-11-16T17:24:03Z
parent: isaac-pcm3
---

Repo: **isaac-dream** (`src/isaac/dream/cli.clj` approve branch, `src/isaac/dream/habits.clj`). Found 2026-11-16 approving Zane's condensed habits: `isaac dream approve <id> --lesson "…"` proposes a brand-new habit `<id>-<uuid8>` that supersedes and retires the original, so the habit loses its id, history, strength and `why`. Spec: `features/habits/operator.feature` (three `@wip` scenarios; the first replaces the old supersede-on-reword scenario).

## What

`--lesson` rewords the habit **in place**, on the same id:

- **Proposed habit**: becomes active with the given lesson; history keeps its events and ends with `:revised` (`:previous` = old lesson). Evidence and `why` are kept.
- **Pending revision**: applies the revision with the operator's lesson instead of the revision's; the revision's `why` is kept; `:revision` removed; `:revised` event with `:previous`.
- **Active, no revision**: the lesson is replaced; `:revised` with `:previous`.
- **Over 300 characters**: refused, exit 1, the error names the 300-character limit; the habit is unchanged.

No new habit id is created, and nothing is retired or superseded.

## Acceptance

`bb features features/habits/operator.feature` green with the three `@wip` tags removed, plus `bb spec` and full `bb features` green. Do not bump the module version (the deploy train does).

feature-baseline: isaac-dream a76b76c3bc7ca88ad67ed8f811f212cb6f915e8e
feature-blob: isaac-dream features/habits/operator.feature 7ccef0d84de852bd11cac3ad149e0c6893150c37 136,158,178

## Landed on main (2026-11-16)

Rewording is in-place for proposed, active and pending-revision habits; oversized lessons are rejected without writes. Focused operator feature and full `bb ci` passed (57 specs, 78 features). Gate passed on the squash commit.

main-sha: isaac-dream b7d8e437e7ffd6f42b23831a7ba76a1e8c68d04f
