---
# isaac-lyau
title: "Tool calls can be persisted without their results, leaving orphan toolCalls in the transcript"
status: completed
type: bug
priority: normal
created_at: 2026-04-30T01:11:33Z
updated_at: 2026-05-05T00:46:37Z
---

## Description

The transcript can end up with toolCall entries that have no
corresponding toolResult entries. Diagnostic logging shows 74
orphans in zanebot's tidy-comet session right now.

## Root cause

src/isaac/context/manager.clj `->compact-message` (line 62) returns
nil for assistant-toolCall entries (no text content). That means
toolCall entries are NEVER in `compactables` and NEVER in
`compacted-ids`. But their paired toolResult entries DO have text
and CAN be in `compacted-ids`.

When src/isaac/session/storage.clj `splice-compaction!` runs:

  insert-at         = first transcript index in compacted-ids
  first-kept-index  = index of firstKeptEntryId
  before            = transcript[0..insert-at)
  after             = transcript[first-kept-index..) - compacted-ids

If the compaction's split point places a toolCall on the kept side
(in `before` or in `after`) but its paired toolResult is in
compacted-ids (removed from `after`), the toolCall survives without
its result.

Confirmed in tidy-comet: orphan toolCalls appear immediately after
the compaction marker — they're the first entries of the kept
segment. Their paired toolResults (which had text) were swept into
the compacted region.

## Fix shape

Three options, ranked by cleanliness:

1. **Pair-aware ->compact-message.** Build compactables that pair
   toolCall+toolResult as one logical unit. When the pair is
   compacted, both entry-ids go into compacted-ids. When kept,
   both stay. compaction-target chooses a split that doesn't sever
   pairs.

2. **compaction-target alignment.** Treat the existing per-message
   compactables but post-process compact-count: if compact-count
   would place the split between a toolCall's result and the next
   message, advance compact-count by one entry to keep the pair
   together.

3. **Splice-side cleanup.** Add a pass at the end of splice-compaction!:
   walk before+after, drop toolCall entries whose result-id isn't
   present in the new transcript. Quick patch but loses the toolCall
   from history without recording its result anywhere.

Recommend (1). Treats the conversation correctly: a tool-use exchange
is one event, not two. (2) is a cheaper variant if (1) is too
invasive. (3) is a band-aid.

## Spec

Once the fix is picked, write a Gherkin scenario that reproduces
the bug: a transcript with a toolCall+toolResult pair where the
strategy's compact-count would place the split between them. Run
compaction. Assert the post-compaction transcript has no orphan
toolCalls. The diagnostic log
:transcript/orphan-toolcalls-detected will fail any naive fix
(it warns when orphans exist on load).

## Diagnostic logging in place (already shipped)

- :transcript/orphan-toolcalls-detected (warn) — fires on every
  get-transcript when orphans are present
- :turn/persisting-tool-pairs (debug) — count before run-tool-calls!
  doseq
- :turn/tool-pair-persisted (debug) — per-pair confirmation

## Definition of done

- The orphan-detection warn no longer fires for newly-compacted
  sessions.
- A Gherkin scenario reproduces the splice path that previously
  produced orphans, and asserts no orphans afterward.
- bb features and bb spec green.
- Manual: in zanebot, after the fix and a fresh session that hits
  compaction, transcript/orphan-toolcalls-detected does not appear.

## Related

- isaac-utzs: compaction summaries lose agent identity (different
  bug; same compaction code path).
- isaac-x4j2: tool loop hits max iterations. Different.

## Notes

Verification failed: automated lyau coverage is green (bb spec passes; the targeted compaction/orphan scenario remains green; session/context specs cover no orphan warn and paired tool-call handling). The current full bb features run is still red, but the failures are unrelated module discovery/activation scenarios, not lyau regressions. However, this bead's definition of done still requires manual zanebot verification that a fresh post-fix compaction no longer emits :transcript/orphan-toolcalls-detected, and that evidence is still not present here.

