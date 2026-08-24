---
# isaac-oum9
title: 'Suite health: repair 24 spec casualties of the session-directories cutover'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-08-24T10:55:23Z
updated_at: 2026-08-24T16:56:50Z
---

Full bb spec on main has 24 pre-existing failures since isaac-k27z (green b44a660, red ec6c66b). Diagnosed 2026-08-24; outside every bean's acceptance — orphaned debt that breaks the previously-green baseline for all other beans.

## Diagnosis (2026-08-24, planning stream)

- Bisect: `bb spec spec/isaac/config/schema_spec.clj` 20/0 at b44a660 -> 20/20 red at ec6c66b (isaac-k27z, session directories). Full-suite count on current main: 24.
- Mechanism A (schema_spec, checks_spec — fail SOLO): config schema conformance now reaches for the filesystem ("isaac.fs/instance: no filesystem available"); the specs predate that and install no fs. Fix: wrap in the nexus/memory-fs context the session specs already use (storage/with-memory-store or nexus/-with-nexus {:fs (fs/mem-fs)}) — or, if schema conform should NOT need fs, lift the fs access out; worker judges which is the honest fix, spec-wrapper is the expected one.
- Mechanism B (bridge_spec, provider_validation_spec — pass solo, fail in full run): suite pollution from the same refactor (ambient nexus state). Fix the specs' context isolation, not run order.
- Mechanism C (genuine contract change): "bridge status-data includes session-file from storage" asserts `:session-file`, which k27z deliberately removed. DELETE or rewrite to the directory-world equivalent — do not resurrect the key.

## Scope

Spec-only repair. No feature files, no production changes EXCEPT if the honest Mechanism-A fix is lifting fs out of schema conform (worker's call, note it in the bean either way). Do not touch permissions/ACL spec expectations beyond what isaac-da0r lands.

## Sequencing

AFTER isaac-da0r completes — da0r's worker has uncommitted edits to checks_spec/schema_spec on work-1; this bean must start from da0r's landed state or it will collide.

## Acceptance

```
cd isaac-agent && bb spec spec
```
0 failures (3 pending allowed). That restores the clean previously-green baseline every other bean's acceptance leans on.

## Implementation (scrapper@isaac-work-2, 2026-08-24)

Spec-only. No production changes. Mechanism A used the expected spec-wrapper (did not lift fs out of schema conform — validation still honestly needs a filesystem).

- `schema_spec`: wrap around in `nexus/-with-nested-nexus {:fs (fs/mem-fs)}`. checks_spec already installed mem-fs per example; left alone.
- `provider_validation_spec`: isolation was broken because `with-manifest` bound only `module-loader/*foundation-index-override*`. `discovery/builtin-index` reads `discovery/*foundation-index-override*` (loader alias is not the same var). Bound both vars in `marigold.agent/with-manifest` and `with-real-manifest*`.
- `bridge_spec`: rewrote the jsonl session-file example to the directory-world path `"testuser/current.ednl"`. Did not resurrect `:session-file` as a stored session key.

Acceptance on bean/isaac-oum9 @ **bae962e4aeb13eab8ae632250ad4c16b37a883ea**:
`bb spec spec` → 1481 examples, 0 failures, 3 pending (claude-cli @real smokes).

Pushed `origin/bean/isaac-oum9`. Sibling checkout left on main @ d90aad2, clean.
