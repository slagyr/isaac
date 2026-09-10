---
# isaac-yk0u
title: 'Tool results stop provoking re-reads: edit returns the updated content; per-window read/grep cache; skill loads dedupe'
status: todo
type: feature
priority: high
tags:
    - agent
    - tools
created_at: 2026-09-10T19:01:17Z
updated_at: 2026-09-10T20:52:27Z
---

Repo: **isaac-agent** (`src/isaac/tool/file.clj` edit/read, `src/isaac/tool/grep.clj`,
`src/isaac/tool/skill.clj`, `src/isaac/llm/tool_loop.clj` / turn cycle map for the
per-window cache).

## Why

Measured on zanebot, both isaac workers, 2026-09-09T20:00Z → 09-10T18:30Z,
8538 tool calls:

| waste | calls | share |
|---|---|---|
| read of a file right after the worker's own edit of it | 1931 | 23% |
| identical read window (file+offset+limit) repeated | 938 | 11% |
| identical grep (pattern+path+glob) repeated | 552 | 6% |
| skill loads (78 hail-bean-work, 68 tdd, 43 clojure) | 236 | 3% |

Every cycle costs ~30 s of grok-4.6 latency at 130k+ tokens of context, so a
120-cycle turn is an hour regardless of progress; ~40% of those cycles
re-learn what the worker already had. Prompting alone did not move it (the
tool-discipline hint shipped in agent 0.1.56; batch size 2.71 → 2.73).
These three make the *tool results* stop provoking the re-read.

## Decisions (2026-09-10, Micah)

1. **Edit returns the updated content.** `fs__edit` / `fs__multi_edit` /
   `fs__write` answer with the edited region — the changed lines plus a few
   lines of context, line-numbered like `fs__read` — not `"edited <path>"`.
   Large writes cap at the read tool's output cap.
2. **Per-window read/grep cache.** A read whose (file, offset, limit) and
   file content hash match a read earlier *in the same context window*
   returns a short stub naming the earlier cycle instead of the content;
   same for grep keyed on (pattern, path, glob, include) when no file under
   `path` changed since. **The cache stores no content — only hashes.** Each
   entry is `key → {:hash <sha-256 of the result> :cycle N}`: the tool still
   runs (read the file, run the grep), hashes the result, and returns the stub
   only when the hash matches. Memory is ~150 bytes per distinct key; a
   pathological 2000-read / 800-grep turn is ~0.5 MB, cleared on compaction
   and at turn end. The cache lives in the turn's cycle map and is
   **cleared on compaction** — a read after compaction is legitimate, the
   content is gone from context. Edits invalidate the edited file's entries.
3. **Skill loads dedupe per window.** A `skill__load` of a skill already
   loaded in this context window returns a one-line stub ("already in
   context since cycle N"); cleared on compaction like (2). The band prompt's
   "re-read the skill at the start of EVERY turn" stays true: a new turn is a
   new window.

Stub wording is part of the contract — it must say the content is already in
context, so the model does not try a different window to get it.

## Open (not in this bean)

- Worker crews could carry `hail-bean-work` + `tdd` in boot files instead of
  tool-loading them (never lost to compaction, prompt-cached). Separate
  decision; changes crew config, not tools.
- A `paths` list on `fs__read` (batch by tool shape). Separate bean if wanted.
- `parallel_tool_calls` on the responses request — not set today.

## Acceptance

Scenarios (@wip, isaac-agent 0b52ae4):
- `features/tool/edit_returns_content.feature` — 1 scenario (decision 1).
- `features/tool/window_cache.feature` — 4 scenarios: repeated read → stub;
  edit between reads invalidates; repeated grep → stub; repeated skill load
  → stub (decisions 2–3). New log event `:tool/cache-hit` with :tool :cycle.
- "Cleared on compaction" is pinned at spec level (the cache map is emptied
  when the turn compacts mid-turn), not as a feature scenario.

```
cd isaac-agent
bb features features/tool/edit_returns_content.feature
bb features features/tool/window_cache.feature
bb features features/tool/
bb spec spec/isaac/tool spec/isaac/drive
bb ci
```

All five pass with @wip removed; existing tool features stay green (the
old "edited <path>" receipt assertion, if any, is updated, not kept). bb ci
green.
