---
# isaac-4a93
title: Flatten berth :manifest {:schema ...} wrapper
status: completed
type: task
priority: normal
created_at: 2026-06-13T11:58:55Z
updated_at: 2026-06-13T12:43:39Z
parent: isaac-n0a1
---

Manifest TODO at resources/isaac-manifest.edn (line ~48): a berth
decl's `:manifest` always holds a single-key map `{:schema ...}`.
Flatten it so `:manifest` directly carries the contribution schema
spec.

- [ ] Every berth decl: `:manifest {:schema X}` -> `:manifest X`
      (server manifest, foundation src/isaac-manifest.edn, marigold
      bridge/longwave manifests, all fixture manifests in specs).
- [ ] Update readers: module/loader.clj
      (`[:manifest :berths <id> :manifest :schema :value-spec :factory]`
      at ~353, `[:manifest :schema]` at ~516 and ~656), berths.clj,
      manifest.clj, and any spec/feature asserting `:manifest :schema`.

Touches the manifest heavily — SERIALIZE with the other manifest beans
(meta-schema, comms-move); they edit the same file.

## Acceptance
- bb spec + bb features green; manifest_self_consistency green.
- No `:manifest {:schema` remains; readers updated.

## Summary
Every berth decl's redundant `:manifest {:schema X}` wrapper flattened to `:manifest X` (server/foundation/marigold manifests + spec/feature fixtures); the three module.loader readers updated [:manifest :schema] -> [:manifest].
