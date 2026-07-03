---
# isaac-n5r2
title: Session compaction metadata diverges from transcript state
status: draft
type: bug
priority: high
created_at: 2026-07-03T06:20:00Z
updated_at: 2026-07-03T06:20:00Z
blocked_by: []
---

## Problem

Direct inspection on zanebot shows recent oversized sessions where the sidecar
metadata and the transcript file do not line up.

Observed on 2026-07-02 / 2026-07-03:

- `orchestration-plan`
  - `:last-input-tokens 2048745`
  - `:compaction-disabled false`
  - `:compaction-count 0`
  - transcript size `452K`
  - no `"type":"compaction"` entries in the JSONL transcript

- `isaac-verify`
  - `:last-input-tokens 2216904`
  - `:compaction-disabled false`
  - `:compaction-count 0`
  - transcript size `792K`
  - no `"type":"compaction"` entries in the JSONL transcript

- `orchestration-verify`
  - `:last-input-tokens 1544483`
  - `:compaction-disabled false`
  - `:compaction-count 1`
  - `:effective-history-offset 1041977`
  - transcript size `1.6M`
  - still no `"type":"compaction"` entries in the JSONL transcript

That last case is the clearest inconsistency: the sidecar claims compaction
state, but the transcript has no compaction entry on disk.

## Why this matters

Right now we cannot trust session inspection surfaces:

- `sessions list` may suggest oversized sessions without telling whether
  compaction actually ran
- sidecar metadata may claim compaction progress that the transcript file does
  not reflect

This is an operator correctness bug, not just a UI ambiguity.

## Investigation goals

Determine which of these is wrong:

1. compaction is not running when it should
2. compaction runs but does not persist transcript entries correctly
3. sidecar metadata is updated even when transcript rewrite/splice does not land
4. transcript search assumptions are wrong because compaction is serialized in a
   different shape than expected

## Likely repo scope

- `isaac-agent`
  - `src/isaac/session/compaction.clj`
  - `src/isaac/session/store/impl_common.clj`
  - `src/isaac/session/store/sidecar.clj`
  - `src/isaac/drive/turn.clj`

## Notes

- This is separate from long-term retained-session rotation/compression work
  (`isaac-xwwb`).
- This is also separate from the `sessions list` size-column improvement; that
  improvement is useful either way, but it does not explain the inconsistency.

## Acceptance

Investigation first. Promote to `todo` once the failure mode is pinned and the
intended repair is concrete enough to write runnable acceptance criteria.
