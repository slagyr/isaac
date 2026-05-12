---
# isaac-m2vc
title: "Restore workspace SOUL context feature"
status: completed
type: bug
priority: normal
created_at: 2026-04-23T01:53:13Z
updated_at: 2026-04-23T03:11:33Z
---

## Description

bb features currently fails in features/session/context.feature on the scenario "soul resolved from workspace SOUL.md". Investigate why turn context resolution no longer picks up the workspace SOUL.md for the crew, and restore the approved behavior.

## Notes

Fixed the context feature helper so workspace SOUL.md resolution runs under the in-memory filesystem, uses the feature state root consistently, and exposes a testable -resolve-turn-context helper for verifier coverage. Verified with bb spec spec/isaac/features/steps/context_spec.clj, bb features features/session/context.feature, bb features, and bb spec.

