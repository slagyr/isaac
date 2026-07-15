---
# isaac-n9ez
title: 'isaac sessions rename: give a session a structured key (+ fix settable tags)'
status: draft
type: feature
priority: normal
created_at: 2026-07-15T05:07:34Z
updated_at: 2026-07-15T05:07:34Z
---

## Goal

`isaac sessions rename <old-key> <new-key>` — atomically rename a stored session to a chosen key, preserving crew, tags, cwd, and transcript. Enables structured session names (tono-work-1, tono-plan-1, …) instead of the auto-generated cast names, which today cannot be changed at all.

## Why (2026-07-14)

Setting up the tonotop orchestration produced auto-named sessions (calm-tapir, homey-toad, …). They function perfectly (bands route by crew+tag, not name), but they are unreadable to operate. There is currently NO way to give a session a structured key:
- `:key` is **immutable** via `sessions set` ("immutable field: key") — no in-place rename.
- Creating a chosen-key session (`isaac prompt --session <key>`, no `--create`) works and captures cwd, but `--session` is mutually exclusive with `--crew`/`--tag`, so it lands in the `main` crew with NO tags — and it cannot be fixed up because (see sibling bug) `sessions set .tags` rejects every set value. So recreate-with-a-name is a dead end too.

## Sibling bug (fix together or as a blocker)

`isaac sessions set <id>.tags <value>` rejects `#{:tono}`, `:tono`, `[:tono]`, and stdin — always "must be a set of keywords". The tags field is meant to be mutable; its value parser does not accept a set of keywords in any form. Fix so tags are settable (this alone would unblock recreate-rename, but a first-class `rename` is the right UX).

## Design

- `sessions rename <old> <new>`: update `:key` and `:name`, rename the `<key>.edn` + `<key>.jsonl` files, update the session index, and reject a collision (new key already exists). Preserve everything else (crew, tags, cwd, transcript, tokens).
- **Refuse if the session is in-flight** (a running turn references the key) — or require it to be idle. Do not rename mid-turn.
- Note downstream references that are NOT auto-updated and must be hand-fixed by the operator: the Discord `:discord/channels` `:session` mapping in isaac.edn, and any pinned `--session` scripts. Print a reminder.

## Scenarios (spec with Micah)

Coverage to settle: rename an idle session preserves crew/tags/cwd/transcript under the new key; the old key no longer resolves; a collision (new key exists) errors cleanly; renaming an in-flight session is refused; `sessions set .tags` accepts a set of keywords (sibling bug).

## Out of scope

Auto-updating config references (discord channel mapping) — operator fixes those; the command prints the reminder.
