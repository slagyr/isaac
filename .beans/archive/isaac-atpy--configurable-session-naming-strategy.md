---
# isaac-atpy
title: "Configurable session naming strategy"
status: completed
type: feature
priority: normal
created_at: 2026-04-21T01:53:27Z
updated_at: 2026-04-21T03:56:57Z
---

## Description

New sessions today get either a caller-supplied name or a random adjective-noun from storage/random-name. That nondeterminism breaks tests that need to assert on session names (e.g., Discord routing scenarios).

Introduce :sessions :naming-strategy config. Strategies:
- :random (default) — current adjective-noun behavior
- :sequential — session-1, session-2, ... on successive unnamed creates (test-friendly)

Only applies when the caller does NOT supply a name. An explicit name wins over the strategy unconditionally.

See features/session/naming.feature for the 2 @wip scenarios. Unlocks deterministic session assertions across the test suite — not Discord-specific.

## Acceptance Criteria

1. Implement :sessions :naming-strategy dispatch in storage/create-session! (or equivalent).
2. Persist the sequential counter across invocations (survives within a state-dir; new state-dir resets).
3. Add the 3 step-defs listed above.
4. Extend the config: step if needed so sessions.naming-strategy flows into the config used by storage.
5. Remove @wip from both scenarios in features/session/naming.feature.
6. bb features features/session/naming.feature passes (2 examples).
7. bb features passes overall.
8. bb spec passes.

## Design

Implementation notes:
- Config path: :sessions :naming-strategy (keyword or string). Default :random.
- Dispatch in storage/create-session! (or storage/session-id): when identifier is nil, call a strategy fn keyed on config value.
- Sequential strategy needs a per-state-dir counter. Simplest storage: a counter file at <state-dir>/sessions/.counter (or fold into the session index). Increment + persist on each unnamed create.
- Random strategy remains storage/random-name as today.
- Explicit names (non-nil identifier) bypass the strategy entirely.

Step-defs to add in spec/isaac/features/steps/session.clj:
- 'a session is created without a name' — (storage/create-session! state-dir nil {}). Captures result as :last-session in gherclj state.
- 'a session is created named "<name>"' — (storage/create-session! state-dir name {}).
- 'session "<name>" does not exist' — assert get-session returns nil.

The 'session "<name>" exists' step already exists (session.clj:360).

config: step (server.clj:37) is the test surface for setting the strategy. If it doesn't currently flow into the loaded-config used by storage, this bead extends it to do so.

## Notes

Implemented configurable session naming with :random and :sequential strategies, persisted sequential counter file, added feature steps for unnamed/named session creation, and taught config: to persist test config into the Isaac home. Verification: bb features features/session/naming.feature, bb features, bb spec, bb spec spec/isaac/session/storage_spec.clj.

