---
# isaac-u0tw
title: "Built-in glob tool: file pattern matching"
status: completed
type: feature
priority: normal
created_at: 2026-04-20T18:30:19Z
updated_at: 2026-04-20T21:06:16Z
---

## Description

Add a glob tool to list files by pattern (e.g. src/**/*.clj) without shelling out. Directory-scoped; returns sorted file paths. Pairs with grep for code navigation.

## Acceptance Criteria

1. Implement src/isaac/tool/glob (or inline in isaac.tool.builtin) with NIO-based pattern matching, mtime-desc sort, default head_limit = 100.
2. Add the three step-defs listed in the design section.
3. Remove @wip from all 5 scenarios in features/tools/glob.feature and the 1 appended to filesystem_boundaries.feature.
4. bb features features/tools/glob.feature passes (5 examples).
5. bb features features/tools/filesystem_boundaries.feature passes (9 examples).
6. bb features passes overall.
7. bb spec passes.

## Design

Implementation notes:
- Java NIO: use java.nio.file.FileSystems/getPathMatcher and Files/walk (both verified in bb).
- Sort: modification time descending. For identical mtimes, alphabetical as tiebreaker (deterministic test output).
- Files only; exclude directories from results.
- Default head_limit: 100. Exposed as a dynamic var (isaac.tool.glob/*default-head-limit*) so tests can rebind.
- Truncation: after head_limit rows, append a 'truncated' indicator and the total match count so the LLM knows scale.
- No matches: return {:result "no matches"} — not an error. Same shape as grep.
- Path: optional arg; defaults to the session cwd (or test state-dir for direct calls).
- Boundary: route through isaac.tool.builtin/ensure-path-allowed on the resolved path.
- Supporting step-defs this bead will need to add to spec/isaac/features/steps/tools.clj:
  1. 'the following files exist:' — table step replacing one-at-a-time file creation. Columns: name (required), content (optional, default ""), mtime (optional ISO-8601 → setLastModified), lines (optional int, generates "line 1"..."line N").
  2. 'the tool result lines match:' — ordered subsequence over result lines. Each row must appear on some line of the result; rows' lines must be non-decreasing (allowing multiple rows per line). Replaces the earlier 'the tool result contains:' table form.
  3. 'the default "<tool>" <key> is <n>' — binds a dynamic var in the named tool's namespace for the scenario. Generic across glob/grep/read.

## Notes

Implemented NIO-backed built-in glob tool with default head limit, truncation metadata, mtime-desc sorting, state-dir/session-cwd defaults, and boundary checks. Added feature steps for bulk file creation, ordered line matching, and generic tool default bindings. Verification: bb features features/tools/glob.feature, bb features features/tools/filesystem_boundaries.feature, bb features, bb spec, bb spec spec/isaac/tool/builtin_spec.clj.

