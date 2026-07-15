---
# isaac-n9ez
title: 'isaac sessions rename: give a session a structured key (+ fix settable tags)'
status: completed
type: feature
priority: normal
created_at: 2026-07-15T05:07:34Z
updated_at: 2026-07-15T16:38:27Z
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

## Approved scenarios (spec'd with Micah, 2026-07-15)

Home: `isaac-agent/features/session/cli.feature` (or `session/mutation.feature`). No new STEPS — `sessions rename` is a new COMMAND exercised through the existing `isaac is run with` step; every assertion reuses existing steps.

### Scenario 1 (approved) — idle rename preserves state
```gherkin
Scenario: rename moves an idle session to the new key, preserving its state
  Given the following sessions exist:
    | name | crew | tags               | total-tokens | last-input-tokens |
    | joe  | main | #{:project/x :wip} | 5000         | 5000              |
  When isaac is run with "sessions rename joe skipper"
  Then the exit code is 0
  And session "joe" does not exist
  When isaac is run with "sessions show skipper --json"
  Then the stdout JSON contains:
    | path         | value                |
    | key          | skipper              |
    | crew         | main                 |
    | tags         | ["project/x", "wip"] |
    | total-tokens | 5000                 |
  And the exit code is 0
```
Token count is the proxy that the transcript carried across.

### Scenario 2 (approved) — in-flight rename refused (necessary: prevents a split-identity mid-turn)
Rationale: an in-flight session is mid-turn; the turn machinery references it by KEY (turn marker sessions/turns/<key>.edn, in-flight flag, delivery :bound-session, the <key>.jsonl the turn is appending to). Renaming the key mid-turn orphans the turn's marker/transcript/in-flight state. Refuse; idle-only.

EXACT error message (prescriptive — assert verbatim):
`cannot rename in-flight session 'joe': a turn is in progress. Wait for it to finish or cancel it first.`

```gherkin
Scenario: renaming an in-flight session is refused, leaving it untouched
  Given the following sessions exist:
    | name | crew |
    | joe  | main |
  And session "joe" is in flight
  When isaac is run with "sessions rename joe skipper"
  Then the stderr contains "cannot rename in-flight session 'joe': a turn is in progress. Wait for it to finish or cancel it first."
  And the exit code is 1
  And session "skipper" does not exist
  When isaac is run with "sessions show joe --json"
  Then the exit code is 0
```

### Scenario 3 (approved) — collision refused, clobbering nothing
EXACT error message (assert verbatim): `cannot rename to 'skipper': a session with that key already exists.`
```gherkin
Scenario: renaming onto an existing key is refused, clobbering nothing
  Given the following sessions exist:
    | name    | crew  |
    | joe     | main  |
    | skipper | ketch |
  When isaac is run with "sessions rename joe skipper"
  Then the stderr contains "cannot rename to 'skipper': a session with that key already exists."
  And the exit code is 1
  When isaac is run with "sessions show joe --json"
  Then the exit code is 0
  When isaac is run with "sessions show skipper --json"
  Then the stdout JSON contains:
    | path | value |
    | crew | ketch |
```
The `crew ketch` assertion proves the target was NOT clobbered.

### Scenario 4 (approved) — help/usage
EXACT usage string (assert verbatim): `Usage: isaac sessions rename <old-id> <new-id>`
```gherkin
Scenario: sessions rename --help shows the rename usage
  When isaac is run with "sessions rename --help"
  Then the stdout contains "Usage: isaac sessions rename <old-id> <new-id>"
  And the exit code is 0
```

### Amendment (not a new scenario)
The existing `session/cli.feature` scenario "sessions --help shows management help and lists subcommands" asserts `list` and `show` appear — add an assertion that `rename` also appears, so the new subcommand is discoverable in management help.

## Spec complete (2026-07-15)
Four approved scenarios (S1 idle-rename-preserves-state, S2 in-flight-refused, S3 collision-refused, S4 help) + the subcommand-list amendment. ZERO new steps across all — `sessions rename` is a new COMMAND run through the existing `isaac is run with` step; every assertion reuses existing steps. Home: `isaac-agent/features/session/cli.feature`. Below the CLI, a `store_spec.clj` unit spec should cover the store-level rename (file + index atomicity, idle-only). Shake-down after ship: rename the live tono sessions (calm-tapir->tono-work-1, spry-firefly->tono-work-2, sincere-marsh->tono-verify-1, homey-toad->tono-plan-1) and repoint the #tonotop channel :session mapping.
