---
# isaac-dubm
title: "Normalize built-in tool parameter naming"
status: completed
type: task
priority: normal
created_at: 2026-04-28T14:39:27Z
updated_at: 2026-04-28T16:12:22Z
---

## Description

Built-in tool parameter keys are inconsistent on two axes:

1. Key type: some tools use keywords (:filePath), others strings ("pattern").
2. Casing: camelCase, snake_case, kebab-case.

**Settled convention: snake_case for both tool names and parameter
names** (and for any tool-facing JSON output keys, to avoid
mixing styles within a single tool surface).

## Audit (today → target)

  read         :filePath :offset :limit                 → "file_path" "offset" "limit"
  write        :filePath :content                        → "file_path" "content"
  edit         :filePath :oldString :newString :replaceAll → "file_path" "old_string" "new_string" "replace_all"
  grep         "output_mode" "head_limit" "pattern"…     → already snake_case (keep ripgrep flag literals: -i/-n/-A/-B/-C/multiline)
  glob         "head_limit"                              → already snake_case
  web_search   "num_results"                             → already snake_case
  memory_get   "start_time" "end_time"                   → already snake_case
  exec         :command :workdir :timeout                → string keys, single-word names already conform
  session_*    "session-key" "reset-model"               → handled by overhaul bead

Tool names already snake_case (web_fetch, web_search, memory_get,
session_state, etc.) — no rename there.

## Scope

src/isaac/tool/builtin.clj — :parameters maps and arg destructuring
in handlers. All keys: strings. All multi-word names: snake_case.

Result JSON keys (e.g. session_info's :created-at, :reset-model in
old session_state) — also snake_case.

spec/isaac/tool/builtin_spec.clj — update fixtures.

features/tools/*.feature — any scenario passing tool args via tables
needs the new key names.

CLAUDE.md / TOOLS.md (if it exists) — document the convention.

## Definition of done

- One representative spec per touched tool exercises the new keys.
- bb features and bb spec green.
- grep -nE 'camelCase|kebab' across tool param maps returns nothing.

## Notes

Scoped verification passed: bb spec spec/isaac/tool/builtin_spec.clj spec/isaac/tool/registry_spec.clj spec/isaac/tool/memory_spec.clj and bb features features/tools/built_in.feature features/tools/execution.feature features/tools/glob.feature features/tools/grep.feature features/tools/memory.feature features/tools/session_info.feature features/tools/session_model.feature features/tools/web_fetch.feature features/tools/web_search.feature. Full bb spec and bb features are currently blocked by unrelated run-turn! rename fallout tracked in isaac-uz44.

