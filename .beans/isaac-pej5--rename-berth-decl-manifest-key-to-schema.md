---
# isaac-pej5
title: Rename berth-decl :manifest key to :schema
status: completed
type: task
priority: normal
created_at: 2026-06-13T12:53:09Z
updated_at: 2026-06-13T12:55:38Z
parent: isaac-n0a1
---

After the :manifest {:schema} flatten (isaac-4a93), a berth
declaration's :manifest key holds the contribution schema spec
directly — so :manifest is a misnomer; it should be :schema.

  :isaac.server/route {:description "..." :manifest {:type :seq ...}}
  ->
  :isaac.server/route {:description "..." :schema {:type :seq ...}}

Care: :manifest is ALSO the module-index entry key (the whole parsed
manifest). Only the BERTH-DECL :manifest (inside :berths, value is a
spec starting {:type ...}) is renamed; the module-index :manifest
stays.

- [ ] All berth decls: :manifest <spec> -> :schema <spec> (server +
      foundation manifests, marigold, bridge, spec fixtures).
- [ ] Readers: loader.clj (the second :manifest in
      [:manifest :berths id :manifest ...], and (:manifest berth-decl)
      / (contains? berth-decl :manifest) sites).
- [ ] Feature assertion paths: .../berths/<id>/manifest -> /schema.

## Acceptance
- bb spec + bb features green; foundation resynced.

## Summary
Berth-decl :manifest renamed to :schema across all manifests, marigold, bridge, spec fixtures, loader readers, and feature assertion paths. The module-index entry :manifest (the whole parsed manifest) is untouched — only the berth-decl key (whose value is always a {:type ...} spec) was renamed.
