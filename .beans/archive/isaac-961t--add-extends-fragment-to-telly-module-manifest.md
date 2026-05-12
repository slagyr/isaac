---
# isaac-961t
title: "Add :extends fragment to telly module manifest"
status: completed
type: task
priority: normal
created_at: 2026-05-05T01:07:01Z
updated_at: 2026-05-05T01:28:31Z
---

## Description

Why: features/modules/schema_composition.feature uses telly as the test module. Telly's manifest needs to declare an :extends fragment so the schema composition logic has something to compose against. Without this, the feature scenarios can't pass.

## Scope

- Telly's module.edn declares:
    :extends {:comm {:telly {:loft {:type :string}}}}
- Optionally add a couple more fields (:color, :mood) to mirror
  what reconciler.feature already exercises with telly slots —
  keeps test surfaces consistent.

## Acceptance

- Telly's manifest carries a real :extends fragment with at least
  :loft (string) so schema_composition.feature scenarios have a
  field to assert against.
- features/modules/schema_composition.feature scenarios use only
  fields telly's manifest actually declares.

## Acceptance Criteria

Telly module.edn declares :extends with at least :loft (string) under {:comm {:telly ...}}

