---
# isaac-0yl8
title: isaac --version / version subcommand
status: draft
type: feature
priority: normal
created_at: 2026-05-13T20:05:16Z
updated_at: 2026-05-13T20:05:16Z
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

## Version source — open question

Three reasonable options, pick one:

### A. Hardcoded constant

A `def` in a new `isaac.version` ns:

```clojure
(ns isaac.version)
(def version "0.1.0")
```

Bumped manually before tagging a release. Minimum machinery. Doesn't
include git info, so two `0.1.0` clones could be on different commits
and look identical.

### B. Resource file written by a release task

`resources/isaac/version.edn`:

```clojure
{:version "0.1.0" :sha "abc1234" :built-at "2026-05-13T..."}
```

A `bb release` task (new) writes this file at release time using
`git rev-parse --short HEAD` and tag info. The CLI reads it via
`io/resource` at runtime. Falls back to `"dev"` if the resource is
missing.

Pro: deterministic, baked in, no runtime shell-outs, works in
distributions where git isn't installed. Con: needs a release ritual;
in-development clones show `"dev"` not the actual SHA.

### C. Git-derived at runtime

CLI shells out to `git describe --tags --always --dirty` on every
`--version` invocation. Output like `v0.1.0-5-gabc1234-dirty`.

Pro: zero release ritual, always accurate, shows dirty state.
Con: requires `git` on `PATH`, requires the install to be a git
checkout, adds ~50-100ms per invocation (only when --version is asked).

### Recommendation

**B** if isaac will ever ship as a non-git distribution (jar, brew
formula, etc). **C** if it's always run from a clone. **A** is the
worst of all three — bump-on-release without the SHA info, easy to
forget.

This bean is filed as **draft** specifically because the source
question isn't decided. Pick one, then promote.

## Acceptance scenarios (sketch)

```gherkin
Scenario: --version flag prints the version
  When I run `isaac --version`
  Then the output starts with "isaac "
  And the exit code is 0

Scenario: version subcommand matches the flag
  When I run `isaac version`
  Then the output equals the output of `isaac --version`

Scenario: --version doesn't trigger a system init
  Given a state directory that would fail to load
  When I run `isaac --version`
  Then the exit code is 0
  And no config-load error is printed
```

The third scenario matters: today the `--help` path returns before
`system/init!`. The version path should do the same so a broken
config doesn't break `--version`.

## Definition of done

- `isaac --version` and `isaac -V` print the version string and exit 0
- `isaac version` subcommand prints the same string
- Neither triggers `system/init!` or config loading
- The version source is implemented per the chosen option
- bb spec and bb features green; new spec covers the three scenarios

## Out of scope

- Plumbing version info into `isaac --help` or banner output (separate
  decision)
- Per-module versions (modules under `modules/` could each have their
  own version; not addressing here)
