---
# isaac-iwz1
title: "config validate --as: switch file-path to data-path semantics"
status: scrapped
type: feature
priority: normal
created_at: 2026-04-21T15:59:15Z
updated_at: 2026-04-21T16:13:32Z
---

## Description

Current --as takes a file path (crew/marvin.edn) and overlays stdin onto that file in the on-disk tree. Mental model mismatch — operators think in terms of the merged config shape, not the on-disk layout. Also no way to validate a standalone EDN blob without on-disk file merging.

New semantics:
- 'isaac config validate -' — validates stdin as the full config in isolation. No on-disk merging. For validating a raw EDN blob standalone.
- 'isaac config validate --as <data-path> -' — overlays stdin at a dotted data path in the merged config, then validates. E.g. --as crew.marvin, --as providers.anthropic.
- File-path form (--as crew/marvin.edn) is rejected with an error mentioning 'data path' so migration is loud, not silent.

No --as-file escape hatch — file-path form dropped entirely. Small CLI breaking change, cheap to migrate (two existing scenarios were already replaced).

See features/cli/config.feature for the 3 @wip scenarios.

## Acceptance Criteria

1. Reject file-path --as (contains '/') with a clear error referencing 'data path'.
2. Implement isolation mode (bare '-' with no --as) — stdin replaces the on-disk root and entity files are not loaded.
3. Implement overlay mode (--as <data-path> -) — stdin assoc-in'd at the data path of the merged config.
4. Update 'isaac config validate --help' copy to describe --as as a data path (ties in with isaac-q77e).
5. Remove @wip from all 3 scenarios in features/cli/config.feature.
6. bb features features/cli/config.feature passes.
7. bb features passes overall.
8. bb spec passes.

## Design

Implementation notes:
- Parse --as value: if it contains '/', reject with 'expected data path like foo.bar, got file path'. Otherwise treat as dotted data-path.
- Bare '-' (no --as): loader short-circuits to 'read stdin EDN as the full root config, skip all on-disk entity-file scans.' Isolation mode.
- '--as <data-path>': loader runs normal load-config-result, then assoc-in's the stdin EDN at the dotted path in the merged config, then re-validates.
- The config.loader load-config-result already returns {:config :errors :warnings :sources}. Wire the overlay at whichever stage matches: probably post-merge, pre-semantic-validation.

The 'the loaded config has no errors' / 'the output contains valid' / 'the stderr contains X' steps all exist already — no new step-defs needed for these scenarios.

Depends on isaac-q77e for the help-page update (document --as as a data path). Not a hard dependency since the scenarios don't exercise help.

