---
# isaac-0soi
title: "Remove orphan-toolcall diagnostic after isaac-lyau lands"
status: completed
type: task
priority: low
created_at: 2026-04-30T14:03:10Z
updated_at: 2026-05-05T22:42:47Z
---

## Description

Once isaac-lyau (pair-aware compaction) lands and the spec scenario
at features/context/compaction.feature:305 is green, remove the
diagnostic logging that was added to investigate the orphan
toolCall bug.

## What to remove

- src/isaac/session/storage.clj `scan-orphan-toolcalls`,
  `transcript-toolcall-ids`, `transcript-toolresult-ids`, and the
  :transcript/orphan-toolcalls-detected warn in `get-transcript`
- src/isaac/drive/turn.clj `:turn/persisting-tool-pairs` and
  `:turn/tool-pair-persisted` debug logs in `run-tool-calls!`
- src/isaac/session/storage.clj :transcript/splice-start and
  :transcript/splice-written info logs in `splice-compaction!`
  (added by 4e23fda for the same investigation)

## Why

The runtime warn fires on every get-transcript call for any session
with existing orphans. tidy-comet has 74 — that means 74 warn lines
per load forever, until the file is migrated or aged out. Once the
spec scenario locks in the fix, the runtime tripwire is noise.

## Existing-orphan cleanup

tidy-comet (and any zanebot session with pre-fix compactions) will
keep its orphan toolCalls in the file. Options:

1. Accept — they're cosmetic. The compactions already happened; the
   missing toolResults aren't recoverable.
2. One-off cleanup task: walk each session JSONL, drop any toolCall
   message whose id has no matching toolResult. Idempotent.

Pick one as part of this bead before removing the warn.

## Definition of done

- All four log keys above are gone from src/
- No references in spec/ left dangling
- bb spec and bb features green
- One of the cleanup options chosen and applied (or explicitly
  accepted as a known no-op)

## Blocked by

isaac-lyau (pair-aware compaction must land first; spec scenario
must be green; otherwise we lose the only signal that the bug
recurred)

## Notes

All diagnostic log keys (scan-orphan-toolcalls, splice-start, splice-written, persisting-tool-pairs, tool-pair-persisted) are already absent from src/. compaction.feature passes 12/12. Pre-existing orphans accepted as cosmetic (option 1 from bead).

