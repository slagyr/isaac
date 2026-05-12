---
# isaac-skul
title: "Home-pointer config file: avoid --home on every command"
status: completed
type: feature
priority: low
created_at: 2026-04-22T18:09:47Z
updated_at: 2026-04-22T18:36:35Z
---

## Description

Isaac today requires --home <dir> on every invocation when using a non-default home directory. For users who want a persistent alternate home, read a pointer file at startup:

Lookup order (first hit wins):
1. --home CLI flag (highest priority, per-invocation)
2. ~/.config/isaac.edn with {:home "/custom/path"} (XDG-conventional)
3. ~/.isaac.edn with {:home "/custom/path"} (fallback convention)
4. ~/.isaac/ (built-in default)

Small bootstrap change in isaac.main (or wherever --home is resolved). Doesn't touch config loader internals — the resolved home is then passed through as today.

Standalone; doesn't depend on anything else.

## Acceptance Criteria

1. Implement home-resolution lookup with --home > ~/.config/isaac.edn > ~/.isaac.edn > ~/.isaac/ precedence.
2. Malformed pointer files fall through to the next option with a warn log.
3. Add the 'the user home directory is ...' step-def.
4. Remove @wip from both scenarios in features/cli/home_pointer.feature.
5. bb features features/cli/home_pointer.feature passes (2 examples).
6. bb features passes overall.
7. bb spec passes.

## Design

Implementation notes:
- Resolve home at CLI entry, before any config load. Likely in isaac.main or a new isaac.home namespace.
- Lookup chain (first hit wins):
  1. Explicit --home <dir> CLI flag
  2. Read ~/.config/isaac.edn — if contains valid EDN with :home key, use it
  3. Read ~/.isaac.edn — if contains valid EDN with :home key, use it
  4. Fall back to ~/.isaac/
- Tilde (~) in pointer values expands to current user home.
- Malformed pointer files: log warn, fall through to next option. Don't fail startup.
- Pointer files with {} or missing :home: ignore (not an error).

One new step-def needed:
- 'the user home directory is "<path>"' — binds a dynamic var (or stubs System/getProperty "user.home") for the scenario. Isaac's home-resolution code reads through this indirection so tests can stub it.

## Notes

Added bootstrap home resolution with --home > ~/.config/isaac.edn > ~/.isaac.edn > current user home, plus malformed-pointer warn/fallthrough behavior and the feature-step hook for stubbing user home. Verified with bb features features/cli/home_pointer.feature, bb features, and bb spec in commit b7e46ea.

