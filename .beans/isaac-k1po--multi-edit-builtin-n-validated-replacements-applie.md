---
# isaac-k1po
title: 'multi_edit builtin: N validated replacements applied atomically in one call'
status: in-progress
type: feature
priority: normal
created_at: 2026-07-08T23:07:55Z
updated_at: 2026-07-09T16:49:16Z
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

- [ ] Scenarios green; fs-bounds respected (same directory allowlist as edit)
- [ ] One-time: zanebot work crews' `:tools :allow` gains `:multi-edit`
