---
# isaac-5jk5
title: Map-form :dynamic-schema directive {:berth :path}
status: completed
type: task
priority: high
created_at: 2026-06-13T11:58:38Z
updated_at: 2026-06-13T12:09:48Z
parent: isaac-n0a1
---

Machinery for the unified config schema (isaac-n0a1). Today
`:dynamic-schema [:path]` infers the source berth from the
config-berth compose context (isaac.schema.dynamic/compose is called
with a berth-key). To let extension-field gather live in
`:isaac.config/schema` (which has no enclosing berth), the directive
must also accept an explicit map form naming the berth.

- [ ] isaac.schema.dynamic/compose accepts `:dynamic-schema {:berth X
      :path [...]}` — gathers contributions from berth X at the given
      path. The vector form `[:path]` keeps current behavior (berth =
      the compose call's berth-key) for migration.
- [ ] Map form ignores the compose berth-key and uses `:berth`.
- [ ] spec/isaac/schema/dynamic_spec.clj covers both forms (the map
      form gathering from a NAMED berth different from any enclosing
      context; collision + base-wins semantics preserved).

Independent of the factory bean (different files); both are
foundation-set. Parallel-safe.

## Acceptance
- bb spec green; new dynamic_spec cases pass.
- Vector-form behavior unchanged (existing comms gather still works).

## Summary
Implemented additively: `compose-map` destructures the directive into `[gather-berth gather-path]` — map form `{:berth :path}` routes the gather to the named berth (ignoring the compose berth-key), vector form unchanged. dynamic_spec covers map-form gather from a named berth, variant annotation, base-wins, collision. Merged 6e0a0207.
