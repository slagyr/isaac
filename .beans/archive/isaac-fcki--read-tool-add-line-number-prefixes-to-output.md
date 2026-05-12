---
# isaac-fcki
title: "read tool: add line-number prefixes to output"
status: scrapped
type: task
priority: low
created_at: 2026-04-20T18:30:27Z
updated_at: 2026-04-20T19:18:08Z
---

## Description

The read tool currently returns raw lines. Adding line-number prefixes (e.g. '42: (defn foo ...)') helps the LLM coordinate edits by line. Claude Code's read tool works this way. Minor refinement; optional :lineNumbers arg for backward compatibility.

## Acceptance Criteria

1. Remove @wip from all 6 new scenarios in features/tools/built_in.feature.
2. Add the 'a binary file {name:string} exists' step in spec/isaac/features/steps/tools.clj that writes a few bytes including null (0x00) and high-bit bytes.
3. bb features features/tools/built_in.feature passes (14 existing + 6 new = 20 examples).
4. bb features passes overall.
5. bb spec passes.

## Design

Implementation notes:
- Line-number format: 'N: content' (colon + space). Each line gets its absolute line number, 1-indexed.
- Default line limit: 2000 (matches Claude Code). After limit, output includes a 'truncated' indicator and the total line count so the LLM knows what it missed.
- Binary detection: heuristic — if the first ~8KB contains a null byte, error with message containing 'binary'. Matches git/grep/rg behavior.
- Empty file signal: emit the literal string '<empty file>' (angle brackets make it unambiguously a sentinel, not content). Return :result, not :isError — reading an empty file is a successful read.
- Offset/limit preserve absolute numbers: when reading lines 10-12, output shows '10: ...', '11: ...', '12: ...' — not 1-indexed slice numbers. Critical so the edit tool can target lines by the number the LLM saw.
- Directory listings unchanged: line-number prefix applies to file contents only. fs/children output stays bare file names.
- Backward compat: existing built_in.feature read scenarios use 'contains' assertions that survive the prefix ('line 10' is a substring of '10: line 10'). Verified before commit.

