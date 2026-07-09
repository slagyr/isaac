---
# isaac-k1po
title: 'multi_edit builtin: N validated replacements applied atomically in one call'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-07-08T23:07:55Z
updated_at: 2026-07-09T16:56:22Z
---

## Goal

A `multi_edit` builtin: N string replacements in one tool call, validated then applied atomically. Transcript evidence (zanebot 2026-07-08): 539 single-replacement `edit` calls plus 94 `sed`-via-exec — models reach for bulk replace and Isaac makes them do it one round-trip at a time (or shell out to sed, bypassing fs-bounds and edit safety).

## Design

- Tool `multi_edit`: `{"edits": [{"file_path", "old_string", "new_string", "replace_all"?}, ...]}` — same semantics per entry as `edit`, files may repeat, cross-file allowed.
- **Validate-all-then-apply-all**: every `old_string` must match (respecting uniqueness rules) against the file state *as prior entries in the same call leave it*; any failure aborts the whole call with an error naming the failing entry index and reason — no partial application.
- Result: per-entry summary (file, replacements made), capped by the standard output cap.
- Registered in builtins; crews opt in via `:tools :allow` `:multi-edit` (zanebot work crews on rollout — one-time acceptance).

## Scenarios (worker writes; required coverage)

1. Three edits across two files in one call: all applied, result summarizes each.
2. Second entry's `old_string` matches nothing: NOTHING is applied (file one unchanged), error names entry 2.
3. Sequential dependence: entry 2's `old_string` matches text created by entry 1 in the same file — applies (validation runs against the evolving state).
4. `replace_all` on one entry replaces every occurrence; a non-unique `old_string` without `replace_all` fails the call (edit's uniqueness rule preserved).

## Acceptance

- [x] Scenarios green; fs-bounds respected (same directory allowlist as edit)
- [ ] One-time: zanebot work crews' `:tools :allow` gains `:multi-edit` (ops rollout)

## Worker notes

`isaac-agent` branch `bean/isaac-k1po`: `multi_edit` builtin, validate-all-then-apply-all with `edit entry N:` errors, `features/tool/multi_edit.feature` + `file_spec`. Relative paths under `:state_dir` when no session cwd. `bb ci` green.

## Verify fail (attempt 1, 2026-07-09): implementation/tests are green, but the bean still lacks the required zanebot tool-allow rollout acceptance

Evidence:
- I verified `isaac-agent` branch `origin/bean/isaac-k1po` at commit `ff2440d`.
- Bean-targeted checks are green:
  - `bb features features/tool/multi_edit.feature` -> `5 examples, 0 failures, 13 assertions`
  - `bb spec spec/isaac/tool/file_spec.clj` -> `35 examples, 0 failures, 67 assertions`
- Broader branch validation is also green:
  - `bb ci` -> `1195 examples, 0 failures, 2354 assertions`
  - final features pass -> `608 examples, 0 failures, 1384 assertions`
- The implementation itself is present and matches the code-side design:
  - `src/isaac/tool/file.clj` adds `multi-edit-tool` with validate-all-then-apply-all semantics and indexed `edit entry N:` errors.
  - `src/isaac/tool/builtin.clj` registers the `multi_edit` builtin and schema.
  - `resources/isaac-manifest.edn` exports `:multi_edit`.
- However, the bean's own Acceptance section still has an unchecked required item:
  - `- [ ] One-time: zanebot work crews' :tools :allow gains :multi-edit (ops rollout)`
- I found no worker note or repo change proving that rollout, and no planner note authorizing pass without it.
- Under the verify guide, unmet acceptance is not passable even when code and test suites are green.
