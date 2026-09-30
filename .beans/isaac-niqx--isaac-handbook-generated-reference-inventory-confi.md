---
# isaac-niqx
title: 'isaac-handbook: generated reference (inventory + config)'
status: draft
type: feature
priority: normal
created_at: 2026-09-29T23:50:57Z
updated_at: 2026-09-29T23:50:57Z
blocked_by:
    - isaac-z90t
    - isaac-dnib
---

## Ruling

Micah, 2026-09-29 (see bean 1's Ruling for the full isaac-handbook
decision). `handbook__read` also serves a **generated** reference built
from the live instance — not module-authored prose — covering the whole
instance (all crews), config + docs only in v1 (no runtime state).

## Problem / Why

Chapters (bean 1) are hand-written prose maintained by each module. They
go stale and can't answer "what is THIS instance actually running/set to
right now" — installed modules and their contributions, configured crews
and what they're allowed, comms/cron/hail bands, and — for config —
what a field's type/default/required/description/options are AND its
current effective value, with secrets shown only as set/not-set.

## Wanted

- **Inventory topics**, one entry per item, each naming its source:
  - `module:<id>` — description + contributions by berth (from
    `isaac.module.berths/module-report`, already exists).
  - `crew:<id>` — model, granted tools, session policy.
  - `comm:<id>`, `cron:<id>`, `hail-band:<id>` — from their owning
    modules' config/berths.
  - A plural topic (`modules`, `crews`, `comms`, `cron`, `hail-bands`)
    returns the whole roster as one item.
- **Config reference topics**, one per config path: `config:<dotted.path>`
  — type, default, required, description, options, and the CURRENT
  EFFECTIVE value (the loaded config, not just the schema). Secret fields
  show `set` / `not set` only, never the value, regardless of topic
  granularity.
- Built from **c3kit apron 3.2.1's `c3kit.apron.schema.doc/describe`**
  plus the loaded config. Do not assume more about that upgrade's
  internals than: (a) apron 3.2.1 ships `schema.doc/describe`, (b) the
  loaded config carries defaults, (c) `isaac config schema` already knows
  default/required today for fields that declare them.
- Same TOC integration as bean 1: no-args `handbook__read` now also lists
  reference-entry topics, not just chapters/sections.

## Acceptance

`isaac-handbook/features/reference.feature` — 5 scenarios drafted this
session, `@wip`, **marked BLOCKED in the file** pending the dependency
below. Do not baseline until it lands:
1. An inventory entry lists a crew's model, granted tools, and session
   policy.
2. An inventory entry lists a module's contributions by berth, naming the
   module (fixtured with `marigold.bridge`/`marigold.longwave` — the
   exact pair `isaac-foundation`'s own `modules_show_manifest.feature`
   already uses for berth contribution).
3. A config entry shows type, default, required, description, and the
   current effective value.
4. A secret config entry shows set/not-set, never the value.
5. A plural inventory topic (`crews`) returns the whole roster.

## Likely repo scope

`isaac-handbook` (the tool's reference-building code) +
`isaac-foundation` (the apron 3.2.1 bump and whatever `isaac config
schema` / describe integration its own bean does — out of this bean's
scope, just a dependency).

## Notes

- **Blocking dependency not yet filed.** The brief for this planning
  session says schema-defaults + "loaded config is effective" is "being
  built in a separate foundation bean right now" — I could not find that
  bean in `isaac/.beans` (checked beans created 2026-09-29, grepped for
  "effective value", "schema.doc", "apron 3.2.1", "default" + "schema").
  `isaac-foundation`'s `deps.edn` still pins apron `3.0.0`. Closest
  existing beans are isaac-3y69 (config-schema-is-generic, completed,
  unrelated — it's about not hard-coding module names in CLI code, not
  about defaults/effective-config) and isaac-7fab (scaffold every default
  onto disk at `isaac init`, `todo`, blocked on other beans — related
  spirit, different mechanism: scaffolding writes files at init, it
  doesn't build a live queryable reference). **Open question below.**
- Reference topic id scheme (`module:`, `crew:`, `config:` prefixes) is
  new — not implied by anything already in the codebase. Flagging for
  veto same as bean 1's `#` section-anchor syntax.
- Config-path secrets: today `${VAR}` substitution already redacts to
  `<VAR:redacted>` / `<VAR:UNRESOLVED>` in normal config readout (see
  `ISAAC.md` § Security posture). This bean's "set/not set" is a
  DIFFERENT, coarser signal purpose-built for the handbook (not even the
  redacted placeholder) — confirm that's intentional and not just reuse
  the existing redaction.

## Decisions (Micah, 2026-09-29)

- Topic ids: a chapter is its module id (`isaac.foundation`); a chapter section is `<module-id>#<slug>`; reference entries use `module:<id>`, `crew:<id>`, `config:<path>`.
- Foundation's chapter always leads the table of contents by an explicit rule (the handbook depends on foundation, so naming `isaac.foundation` here is fine); the other chapters follow sorted by module id.
- Secrets in the reference reuse `config get`'s existing redaction (`<VAR:redacted>`); no second "set / not set" signal.
- Size cap: config key `handbook.max-chars`, default 40000 characters.
- The schema-defaults dependency is **isaac-dnib** (in progress); this bean waits for it and for bean 1.

## Addendum (Micah, 2026-09-29)

The inventory also reports two pieces of live state, cheap and clearly useful now (the fuller `status` topic comes later):
- **Runtime:** "babashka <version>" or "JVM <version>" (babashka sets the `babashka.version` system property).
- **Running components:** from `isaac.component.runtime/started-components` (id, owning module, boot order).
Add scenarios for both when this bean's scenarios are finalized after isaac-dnib lands.
