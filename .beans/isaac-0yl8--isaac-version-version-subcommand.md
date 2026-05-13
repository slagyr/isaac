---
# isaac-0yl8
title: isaac --version / version subcommand
status: in-progress
type: feature
priority: normal
created_at: 2026-05-13T20:05:16Z
updated_at: 2026-05-13T20:33:39Z
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

The output is one line. Trailing newline. Exit 0.

```
isaac 0.1.0 (abcd1234)   ; running from a git checkout
isaac 0.1.0              ; running outside a git checkout
```

## Version source

Read `:version` from the existing `src/isaac-manifest.edn` (it
already has `:version "0.1.0"`). The manifest is the single source
of truth; bumping is just editing `:version`.

When the current working directory contains a `.git` directory,
append the short (8-char) commit SHA in parens. When not, omit it.

The SHA lookup is cwd-based, cheap, and best-effort:

- `cwd = (System/getProperty "user.dir")`.
- Read `<cwd>/.git/HEAD` directly. If it starts with `ref: `, follow
  into `<cwd>/.git/<ref>`. Detached-HEAD HEAD files are a raw 40-char
  SHA — take it as-is.
- Slice the first 8 chars.
- Any IO failure (no `.git`, missing ref, malformed file) → return
  `nil`, output omits the suffix. No shell-out to `git`.
- Wrap `version/cwd` in a 0-arg fn so tests can rebind it without
  touching the real `user.dir`.

Note: the `.git`-in-cwd rule means `isaac --version` reports a SHA
only when invoked from within an isaac checkout. From a packaged
install (e.g. jar), or from any cwd outside a clone, the SHA is
omitted. That's intentional — the SHA describes the running source
tree, not some build-time stamp.

## Acceptance scenarios

Committed under `@wip` in `features/cli/version.feature`:

- `features/cli/version.feature:11` — `--version` prints the manifest
  version
- `features/cli/version.feature:18` — `-V` short flag matches
- `features/cli/version.feature:25` — `version` subcommand matches
- `features/cli/version.feature:32` — `--version` works even when no
  config is present (verifies the flag path bypasses `system/init!`)

All scenarios use the existing `the stdout matches:` step with a
regex pattern like `^isaac \d+\.\d+\.\d+`, so bumping `:version` in
the manifest doesn't churn the scenarios.

### Unit spec coverage

Feature tests run from the project root (a git checkout), so they
inherently exercise the "SHA present" path. Cwd-controlled testing of
SHA-present-vs-absent lives in `spec/isaac/version_spec.clj`:

```clojure
(describe "isaac.version"
  (context "format-version"
    (it "returns the bare version when no SHA"
      (should= "isaac 0.1.0" (version/format-version "0.1.0" nil)))
    (it "appends the short SHA in parens when present"
      (should= "isaac 0.1.0 (abcd1234)"
               (version/format-version "0.1.0" "abcd1234"))))

  (context "manifest-version"
    (it "matches a semver pattern"
      (should (re-find #"^\d+\.\d+\.\d+$" (version/manifest-version)))))

  (context "short-sha"
    (it "returns the 8-char SHA when cwd has a .git directory"
      ;; tmpfs setup: .git/HEAD → "ref: refs/heads/main",
      ;; refs/heads/main → "abcd1234..."
      (with-redefs [version/cwd (constantly tmp-dir)]
        (should= "abcd1234" (version/short-sha))))

    (it "returns nil when cwd has no .git directory"
      (with-redefs [version/cwd (constantly "/tmp/no-such-repo-xyz")]
        (should-be-nil (version/short-sha))))

    (it "returns nil when .git/HEAD is malformed"
      (with-redefs [version/cwd (constantly tmp-with-broken-head)]
        (should-be-nil (version/short-sha))))

    (it "handles detached HEAD (raw SHA in HEAD)"
      (with-redefs [version/cwd (constantly tmp-with-detached-head)]
        (should= "deadbeef" (version/short-sha))))))
```

## Definition of done

- `isaac --version` and `isaac -V` print `isaac <manifest-version>`
  (with the 8-char SHA suffix when run from a git checkout) and
  exit 0
- `isaac version` subcommand prints the same string
- None of the three trigger `system/init!` or config loading
- `src/isaac/version.clj` exists with `manifest-version`,
  `short-sha`, `format-version`, and a rebindable `cwd` 0-arg fn
- Cwd lookup uses `(System/getProperty "user.dir")` by default
- `features/cli/version.feature` passes; `@wip` removed when done
- `spec/isaac/version_spec.clj` covers the SHA-present, SHA-absent,
  detached-HEAD, and malformed-HEAD cases
- bb spec and bb features green

## Out of scope

- Plumbing version info into `isaac --help` or banner output (separate
  decision)
- Per-module versions (modules under `modules/` could each have their
  own version; not addressing here)
