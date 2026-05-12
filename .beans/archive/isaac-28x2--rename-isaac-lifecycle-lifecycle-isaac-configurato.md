---
# isaac-28x2
title: "Rename isaac.lifecycle/Lifecycle -> isaac.configurator/Reconfigurable"
status: completed
type: task
priority: low
created_at: 2026-05-06T19:43:51Z
updated_at: 2026-05-06T21:04:33Z
---

## Description

Why: 'lifecycle' describes the protocol's purpose, not what the instance is. The namespace and protocol both deserve names that match the actual contract — instances bound to a config slot, driven through start/change/stop transitions by the reconciler.

Renaming:
- Namespace: isaac.lifecycle -> isaac.configurator
  (the namespace owns both the protocol AND the reconcile! fn; 'configurator' names the active actor that drives reconfigurable instances)
- Protocol: Lifecycle -> Reconfigurable
  (captures the runtime-react aspect — instances can be reconfigured as the cfg slot changes)
- isaac.api.lifecycle re-export becomes isaac.api/Reconfigurable when the api.* merge lands; in the meantime, isaac.api.lifecycle re-exports isaac.configurator/Reconfigurable as 'Reconfigurable'

## Scope

- Move src/isaac/lifecycle.clj -> src/isaac/configurator.clj
- Rename defprotocol Lifecycle -> defprotocol Reconfigurable inside the new ns
- Update isaac.api.lifecycle (or isaac.api after the merge) to re-export the renamed protocol — keep the public symbol name aligned (Reconfigurable)
- Update all :requires across src/, spec/, features/, modules/:
  - [isaac.lifecycle :as lifecycle] -> [isaac.configurator :as configurator]
  - lifecycle/Lifecycle -> configurator/Reconfigurable
  - lifecycle/reconcile! -> configurator/reconcile!
  - extend-type / extend-protocol forms updated accordingly
- Modules' :requires migrate via the api.* surface, not directly:
  - Discord and telly should use [isaac.api.lifecycle :as ...] -> :as configurator after rename, then later -> isaac.api when that merge lands
  - The protocol they extend changes from Lifecycle to Reconfigurable
- Rename method names? on-startup! / on-config-change! still fit — keep them.

## Out of scope

- Renaming reconcile! itself (the fn). 'reconcile!' is the right verb for what it does; only the namespace and protocol get the rename.
- Renaming method names (on-startup! / on-config-change!) — they describe events accurately.

## Acceptance

- src/isaac/lifecycle.clj is gone; src/isaac/configurator.clj exists.
- defprotocol Reconfigurable is the only public protocol in that ns; Lifecycle does not appear anywhere.
- All :requires across the project use the new ns name and protocol name.
- Comm impls (Discord, telly, any test impls) extend Reconfigurable.
- bb spec passes.
- bb features passes.
- isaac.api re-export points at the renamed protocol with the renamed symbol.

## Acceptance Criteria

isaac.lifecycle ns gone, replaced by isaac.configurator with defprotocol Reconfigurable; all callers, impls, and re-exports updated; specs and features pass

## Notes

Verification failed on re-review: bb spec and bb features both pass, src/isaac/lifecycle.clj is gone, src/isaac/configurator.clj exists with defprotocol Reconfigurable, and isaac.api re-exports Reconfigurable correctly. However, the old protocol name still appears in source: src/isaac/comm/registry.clj line 14 says 'Factory is (fn [host] -> Lifecycle).' The bead acceptance explicitly says 'Lifecycle does not appear anywhere' and that all callers/impls/re-exports are updated, so the rename cleanup is incomplete.

