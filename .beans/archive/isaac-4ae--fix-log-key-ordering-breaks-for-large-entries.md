---
# isaac-4ae
title: "Fix log key ordering breaks for large entries"
status: completed
type: bug
priority: high
created_at: 2026-04-09T01:46:59Z
updated_at: 2026-04-09T01:48:07Z
---

## Description

## Problem\nLog entries with many context fields (9+ keys) lose :ts :level :event ordering because build-entry in logger.clj uses into and assoc on an array-map, which promotes it to a PersistentHashMap once the key count exceeds 8.\n\n## Root Cause\nlogger.clj build-entry uses (-> base (into extra) (assoc :file ... :line ...)) which silently promotes the array-map to a hash-map for entries with many context fields.\n\n## Fix\nUse apply array-map with all fields concatenated in order so insertion order is always preserved regardless of entry size.\n\n## Acceptance\n- Spec covering entries with >8 keys preserves :ts :level :event ordering\n- All existing specs pass

