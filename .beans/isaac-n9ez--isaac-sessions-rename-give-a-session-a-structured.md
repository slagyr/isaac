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

## The real motivation — operability, not vanity (Micah, 2026-07-15)

The auto-names make the orchestration UNOPERABLE from Discord. A notification like
`scrapper@calm-tapir handed off to verify` in #tonotop conveys nothing: not the
role (work/verify/plan), not which of two workers, not the cwd. The operator
cannot tell what a session is or where it's rooted. Structured keys
(`scrapper@tono-work-1`) make every notification self-explanatory. This is the
justification for the rename — it is an operability fix, not cosmetics.

Related lighter fix (consider separately, may partly relieve before rename ships):
enrich the hail-bean notification format to include the session's cwd/role
alongside `crew@session`, so even auto-named sessions read legibly in Discord.

## Correction (2026-07-15): tags ARE settable — there is NO sibling bug

Earlier notes claimed `sessions set .tags` was broken. That was a syntax error on my part.
Tags are set by a PATH SEGMENT, not a value: `isaac sessions set <id>.tags.<keyword>` adds a tag
(see features/session/mutation.feature). So the recreate-with-a-name path already works today:
`isaac prompt --session <key>` (captures cwd) + `sessions set <key>.crew <crew>` + `sessions set <key>.tags.<kw>`.

**Therefore this feature is NOT about enabling structured names** (recreate already does that). It is about
**in-place rename that PRESERVES the transcript/context** — renaming a live session without deleting and
re-seeding it. That is the sole value-add over recreate.

## Design

- `sessions rename <old> <new>`: update `:key` and `:name`, rename the `<key>.edn` + `<key>.jsonl` files, update the session index, and reject a collision (new key already exists). Preserve everything else (crew, tags, cwd, transcript, tokens).
- **Refuse if the session is in-flight** (a running turn references the key) — or require it to be idle. Do not rename mid-turn.
- Note downstream references that are NOT auto-updated and must be hand-fixed by the operator: the Discord `:discord/channels` `:session` mapping in isaac.edn, and any pinned `--session` scripts. Print a reminder.

## Scenarios (spec with Micah)

Coverage to settle: rename an idle session preserves crew/tags/cwd/transcript under the new key; the old key no longer resolves; a collision (new key exists) errors cleanly; renaming an in-flight session is refused; `sessions set .tags` accepts a set of keywords (sibling bug).

## Out of scope

Auto-updating config references (discord channel mapping) — operator fixes those; the command prints the reminder.
