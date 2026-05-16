---
# isaac-xwwb
title: Transcript rotation and compression for retained sessions
status: draft
type: feature
created_at: 2026-05-16T17:23:24Z
updated_at: 2026-05-16T17:23:24Z
blocked_by:
    - isaac-q90z
---

## Problem

Once `:history-retention :retain` (isaac-q90z) ships, transcripts grow unbounded. Long-lived sessions accumulating thousands of turns + large tool results land in the hundreds of MBs. The hot read path is bounded by the post-compaction offset, so chat performance stays fast, but disk footprint and sync-client overhead (Dropbox/iCloud/Syncthing) become real.

## Approach (high-level, to be refined when implementation gets close)

Two complementary mechanisms, both hanging off the post-compaction offset that isaac-q90z introduces:

1. **Rotation at compaction boundaries.** Each compaction marks a clean "epoch break." When a session crosses a size threshold, fold the pre-offset region into a `.epoch-N.edn` file and start a fresh transcript file. The sidecar gains an epoch index so replay/forensics can still walk the full history.

2. **Compression of cold epochs.** Once an epoch is sealed, gzip the file. Cold reads (replay, audit) decompress on demand; hot reads never touch them.

## Config

```clojure
{:defaults {:history-retention {:mode             :retain
                                 :rotate-bytes     104857600  ; 100MB per file
                                 :compress-cold?   true}}}
```

Or `:history-retention` stays a keyword and rotation config lives under a separate `:transcript-rotation` key. Decide at implementation time.

## Blocked by

- `isaac-q90z` (history retention with :retain mode and offset bookkeeping)

## Scope

- Rotation triggered on compaction when post-compaction offset crosses threshold
- Epoch files written atomically, sidecar updated in same transaction
- Cold-read paths (replay, debug tooling) walk the epoch index
- Compression as a follow-up step within this bean — or its own sub-bean

## Out of scope

- Live decompression for hot-path LLM reads (hot path stays uncompressed by construction)
- Cross-session deduplication / shared-content stores

## Feature file

`features/session/transcript_rotation.feature` (scenarios deferred)
