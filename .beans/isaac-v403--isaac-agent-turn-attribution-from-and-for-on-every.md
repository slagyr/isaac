---
# isaac-v403
title: 'isaac-agent: turn attribution — :from and :for on the turn record'
status: in-progress
type: feature
priority: normal
created_at: 2026-10-08T20:41:16Z
updated_at: 2026-10-08T22:16:47Z
parent: isaac-zt1x
---

First step of the contacts epic (isaac-zt1x). Planned with Micah 2026-10-08. Split in three the same day: this bean is the fields; the resolver berth and inheritance are their own beans.

## Problem

A turn does not record who started it in a form anyone can rely on. The turn record has `:origin`, documented as opaque: the submitter supplies it and the agent stores it without interpreting it. Each comm fills it its own way. Nothing downstream can ask "who said this" without knowing comm-specific shapes or parsing message text.

## What to build

Two top-level fields on the turn record, beside `:origin` (ruled by Micah: top-level, not inside origin):

- `:from` — who started the turn. Always present.
- `:for` — the outside party it is done for. Absent when nobody.

Each is a map with a `:kind`:

```clojure
{:kind :handle :comm :gchat :id "users/123" :name "…" :email "…" :authenticated true}
{:kind :crew :id "yopp"}
{:kind :cron :id "nightly-dream"}
{:kind :cli}
{:kind :http :id "<principal>"}
```

A handle is an outside sender exactly as the comm knows them. `:comm` and `:id` are required on a handle; `:name` and `:email` are optional; `:authenticated` is the comm's own claim about whether the identity is verified.

Rules:

- Both submit paths accept them: `isaac.agent.turn.submit/submit!` and the charge used by direct dispatch (`charge-schema` gains `:from` and `:for`).
- When `:from` is a handle and the submitter names no `:for`, `:for` is that same handle.
- When the submitter names no `:from`, the entry point supplies its own: `isaac prompt` records `{:kind :cli}`. A turn never has a nil `:from`.
- The agent stores and shows the maps and does not interpret them beyond the default above.
- `isaac turns show <id>` prints them as `from.<key>` and `for.<key>`, the way it prints `origin.<key>`. `turn__get` returns them with the record.

## Not this bean

- Resolving a handle to a named contact (the resolver berth bean).
- Copying `:for` onto turns a crew member starts (the inheritance bean).
- Comms supplying real handles (isaac-dlw5 for Google Chat).
- Cron and HTTP entry points live in other modules; they adopt `:from` in their own beans. Until then their turns carry whatever default the agent's submit path gives a submitter that names none — state that default in the handbook.
- Migrating readers off `:origin`, or hail's `origin.from`.

## Notes for the implementer

- New step: `a turn is submitted with:` — a key/value table naming `id`, `input`, `session` and dotted `from.*` / `for.*` keys, calling `submit!`. The existing submit steps cannot name the turn id or the sender.
- `:authenticated` parses as a boolean in the step table.

## Likely repo scope

`isaac-agent`.

## Acceptance

Run from `isaac-agent`, with `@wip` removed from the feature file:

- `bb features features/turn/attribution.feature`
- `bb features features/turn/turn_inspection.feature` still green.
- `bb verify` and `bb jvm-spec` green.
- The agent handbook chapter documents `:from` and `:for`: the kinds, the handle fields, the for-defaults-to-from rule, and how `turns show` prints them.

feature-baseline: isaac-agent e941bd2aeb79a9f8a1e3238a23213b3df734e7c1
feature-blob: isaac-agent features/turn/attribution.feature 11a447b9739175f07bb1e143439a5dfb815c5a33

## Landed on main (2026-10-08)

main-sha: isaac-agent 4d51451be1af9dd3324fa8a9ec91b77ed6f5894c
