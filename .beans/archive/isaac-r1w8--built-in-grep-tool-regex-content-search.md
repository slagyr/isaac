---
# isaac-r1w8
title: "Built-in grep tool: regex content search"
status: completed
type: feature
priority: normal
created_at: 2026-04-20T18:30:17Z
updated_at: 2026-04-20T21:05:25Z
---

## Description

Add a grep tool so crew don't shell out to rg/grep via exec. Should be directory-scoped (honor :tools :directories), accept a regex pattern and optional file-glob filter, and return matches with file:line prefixes. Ripgrep-like defaults (respect gitignore, skip binaries).

## Acceptance Criteria

1. Implement src/isaac/tool/grep (or inline in isaac.tool.builtin) with register! registration.
2. Add the 'the tool result contains:' table step in spec/isaac/features/steps/tools.clj.
3. Remove @wip from all 9 scenarios in features/tools/grep.feature and the 1 scenario appended to features/tools/filesystem_boundaries.feature.
4. bb features features/tools/grep.feature passes (9 examples).
5. bb features features/tools/filesystem_boundaries.feature passes (5 examples, including new grep boundary).
6. bb features passes overall.
7. bb spec passes.

## Design

Implementation notes:
- Shell out to ripgrep (rg). Require rg on PATH or fail fast at register time.
- Parameter schema: pattern (required), path (required), glob, type (clj/py/etc shorthand), -i bool, -n bool, -A int, -B int, -C int, multiline bool, output_mode (content|files_with_matches|count), head_limit int (default 250, 0=unlimited), offset int (default 0).
- Default output is 'content' mode: rg default output (file:line:match).
- 'no matches' result: rg exits 1 with no output; tool returns {:result "no matches"} not {:isError true}.
- Truncation: after head_limit rows, append a visible 'truncated' indicator line. Claude Code uses 'Results are truncated.' — exact wording flexible; the scenario just asserts the substring 'truncated'.
- Direct-call scenarios in features/tools/grep.feature use real disk via 'a clean test directory'. rg is a subprocess and can't read mem-fs.
- Directory boundary: route through isaac.tool.builtin/ensure-path-allowed on the 'path' arg so session-scoped invocations honor :directories. Direct calls (no session-key in args) remain unguarded, matching the pattern used by read/write/edit.
- Step definition additions expected: a single new 'the tool result contains:' table step (single 'text' column; each row asserted as a substring of the tool result). The generic 'the tool NAME is called with:' step already exists.

## Notes

Verification failed: all 9 scenarios in features/tools/grep.feature are pending (0 assertions) because the feature uses step 'Then the tool result lines match:' but spec/isaac/features/steps/tools.clj:125 defines 'the tool result contains:'. Either rename the step definition or update the feature to match. bb features grep.feature reports '9 examples, 0 failures, 0 assertions, 9 pending'.

