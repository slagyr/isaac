---
# isaac-r4qj
title: manifest schemas must not shadow reserved base keys
status: todo
type: bug
priority: normal
created_at: 2026-05-18T22:19:52Z
updated_at: 2026-05-18T22:49:52Z
---

## Problem

A module's manifest could declare schema fields whose keys collide with isaac's static base schema for the same slot. For example, telly's manifest could declare `:crew` under `:comm :telly :schema`, which would shadow the base `comm-instance` (src/isaac/config/schema.clj:219) field. Today:

- `:type` shadowing is silently no-op: `check-comms` strips `:type` from the slot before validating against the manifest (loader.clj:841). Manifest's `:type` field never gets exercised.
- `:crew` shadowing IS effective: the manifest's spec would replace the base spec. A typo'd module could change the meaning of a core field with no error.

After **isaac-4cao** (B3) makes manifest schemas first-class apron, `cs/verify-schema-refs` validates ref usage, but doesn't catch base-key shadowing.

## Fix

At module-index build / manifest-validation time, reject any module schema whose keys overlap with the static base schema for that slot:

- `:comm <type> :schema` may not declare `:type` or `:crew`
- `:provider <type> :schema` may not declare `:template` (and probably others — needs survey)
- `:tools <name> :schema` — survey static base
- `:slash-commands <name> :schema` — `:command-name` is currently legitimately user-overridable, but echo manifest currently declares it under its `:schema` — survey needed

Error message names the module, the slot, the offending key, and the reserved base.

## Status

Draft. Needs survey of reserved base keys per surface, then scenarios.

## Related

- **isaac-4cao** (likely lands first; this can fold into the manifest-validation pass introduced there, but is scoped separately so 4cao stays focused on the apron migration).


## Reasons for Scrapping

Same principle as isaac-v6fl: overrides are a feature. If a module wants to redeclare `:type` or `:crew` on a comm slot (or any base reserved key on other surfaces), that's allowed.

Current behavior is fine: `:type` strips before manifest validation (base wins); `:crew` validates twice when both declare it (no semantic conflict, just doubled messages). Neither is broken.


## Reopened — narrowed scope

`:type` is the discriminator that selects which manifest applies to a slot. Overriding it via a manifest's `:schema {:type ...}` declaration is circular and nonsensical. Today the loader silently strips `:type` before manifest validation; the change is to make this explicit:

- At manifest-load time (or `verify-schema-refs` time), reject any manifest whose `:schema` declares `:type`. Error names the module, the slot, and points the author at the discriminator role of `:type`.
- All other base keys (`:crew`, `:template`, etc.) — allowed to override. Documented as a feature.

Drop the silent strip in `check-comms` (loader.clj:841) since the manifest can no longer carry a `:type` field by construction.
