---
# isaac-1pqn
title: "Crew memory tools: write, get, search"
status: completed
type: feature
priority: normal
created_at: 2026-04-21T22:40:26Z
updated_at: 2026-04-21T23:58:47Z
---

## Description

Per-crew persistent memory stored as daily markdown notes under the crew's quarters. Three new built-in tools:

- memory_write: append content to today's note at <state-dir>/crew/<id>/memory/YYYY-MM-DD.md. Accepts content as a single string OR an array of strings (batched writes, saves LLM round-trips during flush-style operations).
- memory_get: read notes in a date range. Params: start_time and end_time (both ISO-8601 dates, inclusive). Missing day-files skipped silently.
- memory_search: ripgrep the crew's memory dir. Param: query. Returns ripgrep-style file:line:match output.

Dedicated tools rather than reusing read/grep — crew may not have those allowlisted. Memory tools work regardless.

UTC-keyed daily filenames. Timezone configurability is a future bead; v1 uses UTC everywhere.

See features/tools/memory.feature for the 6 @wip scenarios.

## Acceptance Criteria

1. Implement the three memory tools, registered via register-all!.
2. Add the 3 step-defs listed above (clock stub, file-matches, EDN-in-cell parsing).
3. Remove @wip from all 6 scenarios in features/tools/memory.feature.
4. bb features features/tools/memory.feature passes.
5. bb features passes overall.
6. bb spec passes.

## Design

Implementation notes:
- Namespace: src/isaac/tool/memory.clj (or inline in tool/builtin.clj) with register-all! hook.
- Storage path: <state-dir>/crew/<crew-id>/memory/YYYY-MM-DD.md. Create the memory/ dir and file on first write.
- UTC date from a clock fn (dynamic var or injected) so tests can stub deterministically.
- content can be either a string or a vector of strings. When vector, each entry is appended on its own line.
- memory_get: walks dates from start_time to end_time inclusive. For each date, read the file if it exists; concatenate. Missing day-files are silently skipped.
- memory_search: shell out to rg on <crew-quarters>/memory/ with the query. Same head_limit / truncation pattern as the grep tool.

Supporting step-defs to add:
1. 'the current time is {iso:string}' — binds a dynamic *now* var for deterministic clock. Parses ISO-8601.
2. 'the file "<path>" matches:' — ordered-subsequence match on file lines (parallel to 'the tool result lines match:').
3. Extend 'the tool {name:string} is called with:' to parse EDN-form arrays/vectors in cells that start with [ or {.

Crew must have memory_write / memory_get / memory_search in their :tools :allow list. Boundary check: tools only operate within <crew-quarters>/memory/, so they work even if :directories isn't otherwise set.

