---
# isaac-s715
title: 'isaac-agent: identifiers berth — a module names the party behind a handle'
status: in-progress
type: feature
priority: normal
created_at: 2026-10-08T22:03:41Z
updated_at: 2026-10-09T17:52:32Z
parent: isaac-zt1x
blocked_by:
    - isaac-v403
---

Part of the contacts epic (isaac-zt1x). Planned with Micah 2026-10-08/09. Blocked by the attribution fields bean (isaac-v403), which supplies `:from`, `:for` and the `a turn is submitted with:` step.

## What this is

When a message arrives, the turn records the sender as a handle: how one comm names the party on the other end, e.g. Google Chat's `users/123`. The agent does not know that `users/123` is a particular person.

This bean adds one berth to `isaac-agent`. Just before a turn record is stored, the agent hands each handle to any installed identifier and asks who it is. An identifier that knows returns a replacement map, e.g. `{:kind :contact :id "cordelia"}`, and that is stored instead. `isaac-contacts` (isaac-o7tm) will be the first real identifier.

## Vocabulary (ruled with Micah)

- **Party** — whoever is on the other end of a message: a person or a machine. Never a comm, space, channel or session.
- **Handle** — how one comm names a party. One party has many handles.
- **Contact** — a party known by name, the same across comms (the contacts module's word).
- **Identifier** — a module that names the party behind a handle. The berth is `:isaac.agent/identifiers`.

"Handle" needs disambiguating in the docs: iMessage's own data also calls a phone number or email a handle, which is the same idea but a narrower thing.

## Rules

- A module declares `:isaac.agent/identifiers {<id> {:factory …}}` in its manifest.
- Applied once, at submit, to `:from` and to `:for` when they are `:kind :handle`. Other kinds are never passed to an identifier.
- No identifier installed, or none that knows the handle: the handle is stored as it arrived.
- An identifier that throws is skipped and logged as `:identifier/failed` at warn, naming the module. The turn is stored with the raw handle. A turn never fails on an identifier.
- The handle's `:authenticated` value is carried onto the replacement by the agent, whatever the identifier returned. An identifier can name a party, never vouch for one.
- An identifier must be a plain in-memory lookup that does not block. No timeout machinery; say so in the handbook.

## Notes for the implementer

- Two fixture modules under `modules/`, in the style of `isaac.section.beacon` and `isaac.section.squall`: `isaac.roster.almanac` knows `cordelia-7` on `logbook` as `{:kind :contact :id "cordelia"}` and returns `:authenticated true` for everyone (the third scenario depends on that); `isaac.roster.fog` always throws.
- Model the berth on `:isaac.agent/system-sections` (`features/module/system_section_extension.feature`).

## Likely repo scope

`isaac-agent`.

## Acceptance

Run from `isaac-agent`, with `@wip` removed from the feature file:

- `bb features features/module/identifier_extension.feature`
- `bb features features/turn/attribution.feature` still green.
- `bb verify` and `bb jvm-spec` green.
- The agent handbook chapter documents the identifiers berth and defines party, handle, contact and identifier in one place, including that a handle names a party and never a comm, and how it differs from iMessage's own "handle".

feature-baseline: isaac-agent a155989081a0d3f8c24550bcf6e810a9e4ac7319
feature-blob: isaac-agent features/module/identifier_extension.feature ee153e38c8da110ed9c5d14b64bc24621616b7f3
