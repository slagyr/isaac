---
# isaac-3blh
title: "Pre-compaction transcript backups (max 8 per session)"
status: completed
type: feature
priority: low
created_at: 2026-04-30T04:59:08Z
updated_at: 2026-04-30T12:20:30Z
---

## Description

Before splice-compaction! rewrites a transcript file, copy the
current file to a timestamped backup. Diagnostic gold for
investigating compaction bugs (currently isaac-lyau orphans), and
a recovery path if a splice ever loses messages.

## Behavior

Before write-transcript! inside splice-compaction! (storage.clj:644):

1. Copy the current transcript file to a backup path:
     <state-dir>/sessions/<session-id>.<timestamp>.bak.jsonl
   Colocated with the live session file. The .bak.jsonl extension
   keeps it readable by jq/grep/bb out of the box.

2. After write, prune older backups for this session: keep at most
   8 most recent backups (hardcoded; configurable later if needed).
   Pruning by mtime or by timestamp suffix (sortable).

## Storage cost

Sessions are ~100KB-1MB. 8 backups = ~1MB-8MB per session at most.
Trivial.

## Spec

A scenario where compaction runs and the backup file appears
afterward:

  Scenario: splice-compaction! creates a backup before rewriting
    Given a session with a transcript ready to compact
    When compaction runs
    Then a file matching "sessions/<session-id>.*.bak.jsonl" exists
    And the backup file matches the pre-splice transcript

  Scenario: only the 8 most recent backups are kept
    Given a session with 9 prior backups
    When compaction runs (creating the 10th)
    Then exactly 8 backup files exist
    And the oldest backup has been pruned

## Definition of done

- splice-compaction! creates a backup before write-transcript!
- Backups capped at 8 per session, oldest pruned
- bb features and bb spec green
- Manual: trigger compaction on a session, verify the .bak.jsonl
  appears with the expected pre-splice content

## Related

- isaac-lyau: the splice-boundary logging plus these backups
  together give us full forensic data on the orphan-toolCall bug.
- isaac-7t1: post-turn tool summarization (deferred). Not directly
  related but compaction-adjacent.

