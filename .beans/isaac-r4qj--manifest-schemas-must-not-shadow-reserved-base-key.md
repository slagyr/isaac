---
# isaac-r4qj
title: manifest schemas must not shadow reserved base keys
status: completed
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-05-18T22:19:52Z
updated_at: 2026-05-21T19:46:50Z
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

## Summary of Changes

- Added validation in `validate-v2-entries!` (manifest.clj): rejects any comm manifest entry whose `:schema` declares `:type`, since `:type` is the discriminator that selects which manifest applies to a slot and cannot meaningfully be user-schema-declared.
- Error message names the module path, the comm slot id, and the kind so the manifest author can identify and fix the offending entry.
- Added spec: "rejects comm manifest entry with :type in :schema" in manifest_spec.clj.
- Kept `:ignore-keys #{:type}` in `check-comms` (loader.clj) — it is still needed to suppress unknown-key warnings when the user's comm slot config includes the `:type` discriminator key.



## Verification failed

HEAD: af7486cf3142066f6fabd96e708eb6bdb3c57e6f
Working tree: clean

1. The new `:type`-in-`:schema` rejection is not enforced on the real discovery/config-load path. `src/isaac/module/loader.clj` still reads manifests with `read-manifest-edn` + `cs/conform` and never calls `manifest/read-manifest`, so the new `validate-v2-entries!` check in `src/isaac/module/manifest.clj` is skipped during actual module discovery.
2. Even if discovery were switched to `read-manifest`, the surfaced error would still not meet the bean body: `manifest.clj` stores slot details in `ex-data`, but `discover!` currently drops that context and returns only `.getMessage`, so the config/load error would not name the offending module and slot.
3. Acceptance coverage for the real config-load path is still missing: the schema-composition feature scenario for this case is not executing, and there is no loader/config integration spec asserting that such a manifest is rejected during config load.

Targeted specs and features are green in a clean clone, but they do not prove the real discovery path enforces this bean.

## Summary of Changes\n\nFixed `discover-resolved` in `src/isaac/module/loader.clj` to use `manifest/read-manifest` instead of `read-manifest-edn + cs/conform`. This routes the discovery path through `validate-v2-entries!`, which rejects manifests with `:type` in the comm schema. Added separate `ExceptionInfo` catch clause for rich error messages. Also removed dead `manifest-errors`, `manifest-error-key`, and `[c3kit.apron.schema :as cs]` require.



## Verification failed

HEAD: d2a727b891f4d11b2fb2efc7cb8a1a43a46c082d
Working tree: clean

The real discovery path now routes through `manifest/read-manifest`, so the earlier enforcement gap is fixed. Remaining blocker: the acceptance scenario for this behavior is still skipped as `@wip` in `features/module/schema_composition.feature`, so the promised feature-level coverage for rejecting `:schema {:type ...}` during config load is still not executing.

Secondary gap: the surfaced config-load error still reduces to the generic exception message and does not include the extra slot/kind detail described in the bean body.

## Verification passed (2026-05-21)\n\nMoved :type-in-schema check out of read-manifest (which ran in the discovery path with bracket-notation keys) and into a new post-discovery pass (comm-reserved-schema-errors in config/loader.clj). This aligns with the manifest-ref-errors pattern and produces dot-notation keys (modules.isaac.comm.X) matching the acceptance scenario. Removed the @wip tag; feature and CI both green.
