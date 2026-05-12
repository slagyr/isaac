---
# isaac-5s56
title: "Grep tool features fail in CI: files_with_matches and count modes"
status: completed
type: bug
priority: normal
created_at: 2026-05-11T18:26:14Z
updated_at: 2026-05-11T18:37:53Z
---

## Description

Two grep feature scenarios pass on macOS (rg 15.1.0, Homebrew) but
fail on ubuntu-latest CI (rg from apt-get, likely 13.x or 14.x):

- features/tools/grep.feature:46 — output_mode files_with_matches
- features/tools/grep.feature:62 — output_mode count

These were latent failures, hidden by an earlier CI workflow bug that
errored out on bb features --tags=~@wip before features ran. Fixed
in 50ae519 (CI: drop --tags flag), which surfaced them. See
isaac-mkvf for the failing CI run.

## Suspected cause

isaac.tool.grep/grep-command builds the rg invocation as:

    [rg --color=never --with-filename ... -l <pattern> <path>]
    [rg --color=never --with-filename ... -c <pattern> <path>]

--with-filename is unconditional. In some rg versions, combining it
with -l (list paths only) or -c (count per file) may produce a
different output format or suppress expected fields. Worth testing
against rg 13.0.0 / 14.1.0 directly.

## Repro

- Mac: bb features features/tools/grep.feature:46 features/tools/grep.feature:62 → 2 pass
- CI: same scenarios fail with \"Expected truthy but was: false\"
- Run: https://github.com/slagyr/isaac/actions (run for 50ae519)

## Likely fix

Drop the unconditional --with-filename. It's only meaningful for
content mode; for -l and -c, rg always includes filenames. Easy
one-line change in grep-command, then test against both rg versions.

## Related

Pairs with isaac-ms3a (make grep_spec hermetic by mocking rg). Once
both land, the grep tool will be portable across rg versions and
testable without the binary on PATH.

## Notes

Adjusted grep-command so --with-filename is only used in content mode, which fixes files_with_matches and count behavior across ripgrep versions. Validation: bb spec spec/isaac/tool/grep_spec.clj, bb features features/tools/grep.feature:46 features/tools/grep.feature:62, bb spec, and bb features features/tools/grep.feature.

