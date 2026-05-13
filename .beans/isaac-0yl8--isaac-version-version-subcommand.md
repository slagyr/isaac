---
# isaac-0yl8
title: isaac --version / version subcommand
status: draft
type: feature
priority: normal
created_at: 2026-05-13T20:05:16Z
updated_at: 2026-05-13T20:12:41Z
---

## Problem

There's no way to ask the running `isaac` CLI which version it is. When
debugging multi-clone setups (worker, planner, zanebot) it's painful
to tell whether a given binary has a particular commit's fix in it.

## Shape

Two complementary surfaces:

- `isaac --version` / `isaac -V` — flag handled in `isaac.main/run`
  alongside the existing `--help` / `-h`. Returns immediately, no
  command dispatch.
- `isaac version` — subcommand registered in `isaac.cli` for symmetry
  with `isaac help`. Prints the same string.

The output should be one line, e.g. `isaac 0.1.0 (abc1234, built 2026-05-13)`,
trailing newline. Exit 0.

## Version source

Read `:version` from the existing `src/isaac-manifest.edn` (it
already has `:version "0.1.0"`). No new constant or release-task
machinery — the manifest is the single source of truth.

When the running process is inside a git repository, append the short
SHA (6 chars). When not, just print the manifest version.

Examples:

```
isaac 0.1.0 (a1b2c3)     ; running from a clone, on commit a1b2c3...
isaac 0.1.0              ; running from a jar / non-git install
```

The SHA lookup needs to be cheap and best-effort:

- Read `.git/HEAD` directly to find the current ref (or detached
  SHA), then read the ref file. Avoids shelling out to `git` and
  works even if `git` isn't on PATH.
- If anything fails (no `.git`, malformed refs, etc.), silently
  omit the SHA suffix.

Bumping the version is just editing `:version` in the manifest.

## Acceptance scenarios (sketch)

```gherkin
Scenario: --version flag prints the manifest version
  When I run `isaac --version`
  Then the output starts with "isaac 0.1.0"
  And the exit code is 0

Scenario: version subcommand matches the flag
  When I run `isaac version`
  Then the output equals the output of `isaac --version`

Scenario: running from a git checkout, the output includes the short SHA
  Given the working directory is a git repository at commit a1b2c3d4...
  When I run `isaac --version`
  Then the output is "isaac 0.1.0 (a1b2c3)"

Scenario: running outside a git checkout, the SHA is omitted
  Given the working directory has no .git directory
  When I run `isaac --version`
  Then the output is "isaac 0.1.0"

Scenario: --version doesn't trigger a system init
  Given a state directory that would fail to load
  When I run `isaac --version`
  Then the exit code is 0
  And no config-load error is printed
```

The last scenario matters: today the `--help` path returns before
`system/init!`. The version path should do the same so a broken
config doesn't break `--version`.

## Definition of done

- `isaac --version` and `isaac -V` print the version string and exit 0
- `isaac version` subcommand prints the same string
- Neither triggers `system/init!` or config loading
- Version string is sourced from `:version` in `src/isaac-manifest.edn`
- When a `.git` directory is present, the short SHA (6 chars) is
  appended in parens; otherwise omitted
- SHA lookup reads `.git/HEAD` and the ref file directly (no shell-out
  to `git`); failures degrade silently to omit the suffix
- bb spec and bb features green; the new acceptance scenarios pass

## Out of scope

- Plumbing version info into `isaac --help` or banner output (separate
  decision)
- Per-module versions (modules under `modules/` could each have their
  own version; not addressing here)
