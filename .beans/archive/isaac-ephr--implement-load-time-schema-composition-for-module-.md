---
# isaac-ephr
title: "Implement load-time schema composition for module :extends"
status: completed
type: task
priority: normal
created_at: 2026-05-05T00:56:24Z
updated_at: 2026-05-05T01:28:31Z
---

## Description

Why: features/modules/schema_composition.feature describes the load-time composition behavior we need so module-contributed config keys validate correctly. Discord (and any other module) declares its slot fields via :extends, and the loader has to merge those fragments into the cfg schema BEFORE validation runs.

## Scope

- At cfg-load time (after cccs's discovery), walk the :module-index
  and gather every manifest's :extends fragment.
- Merge :extends.{:comm {<impl> {<field> <spec>}}} fragments into the
  comm-slot schema, dispatched by :impl. A slot whose :impl is :telly
  validates against core comm fields plus telly's contributed fields;
  same for :discord, etc.
- Migrate the static :comms schema to the slot-keyed model
  (currently {:comms {:discord discord}}; locked target is
  {:comms {<slot-id> {:impl ... + impl-specific fields}}}).

## Behavior

- Composition runs at load time (not activation time). Schema is
  pure data; no module source needed.
- Validate-only: invalid values produce validation errors. No
  coercion (e.g., :loft 42 errors instead of being silently
  coerced to "42").
- Without a module declared in :modules, its slot fields are NOT
  in the schema and surface as unknown-key warnings.

## Out of scope

- Validators referencing module-source functions (factory refs,
  symbol-resolved validators) — gated on upstream c3kit work.
- Top-level extension points other than :comm (providers, tools)
  — same shape, separate beads when needed.

## Acceptance

- features/modules/schema_composition.feature scenarios pass
  without @wip
- Existing comm-related scenarios in reconciler.feature and
  Discord features still pass (with the schema migration to
  slot-keyed model)

## Acceptance Criteria

features/modules/schema_composition.feature passes without @wip; existing reconciler/Discord scenarios still pass after schema migration to slot-keyed comms model

