---
# isaac-p5v2
title: 'Composed-root config-schema: fold into foundation schema_compose_spec; park real-fragment smoke'
status: in-progress
type: task
priority: normal
created_at: 2026-06-15T16:18:04Z
updated_at: 2026-06-15T16:49:19Z
---

The 3 composed-root assertions left over from the schema_spec decomposition do NOT need a host/assembly
repo. Disposition:

From baseline isaac/spec/isaac/config/schema_spec.clj @ 09795481:

1. "each entity is a named wrapped map spec" — a shape invariant over the composed root.
   -> FOLD into isaac-foundation schema_compose_spec: assert composed entities are wrapped maps,
      over the fixture module-index schema_compose already builds. (Generic composition property.)

2. "root rejects invalid types with per-field errors" — error aggregation across fragments.
   -> FOLD into schema_compose_spec: compose a fixture multi-fragment index, conform invalid data,
      assert the per-field message-map spans fragments. (This is schema-compose's behavior — its job.)

3. "root conforms a complete config" — DEFERRED, not folded.
   The only unique value is 'all the REAL shipped fragments compose into a valid root without collision',
   which needs every module on one classpath — only the eventual top-level isaac app provides that.
   Park a single smoke test there when the app exists. A fixture-index version is redundant with #1/#2
   plus the per-module conformance now in each module's schema_spec.

Rationale: boot is already data-driven (module.loader/discover! + start-modules! fire each berth :factory);
there is no hand-written assembly to build. Composition is pure manifest data, and schema-compose already
owns/tests it with a fixture index. No new host repo.

Acceptance: foundation schema_compose_spec gains the shape-invariant (#1) and cross-fragment error-aggregation
(#2) checks over a fixture index, green; #3 captured as a deferred smoke for the top-level app (note only,
no host built). gateway excluded (legacy trash).
