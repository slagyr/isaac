---
# isaac-b3n0
title: 'isaac.rb: homebrew formula — brew install isaac (the seed)'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-06-18T16:48:52Z
updated_at: 2026-06-18T17:03:41Z
---

The entry point of the product vision: `brew install isaac` gives the user the
foundation seed CLI. Add an `isaac.rb` formula to slagyr/homebrew-tap (alongside
braids.rb), modeled on braids.rb's bb-launcher pattern.

## Shape (mirror slagyr/homebrew-tap/braids.rb)

• `depends_on "borkdude/brew/babashka"` (no JVM dep — launcher is pure bb;
  confirmed bb-viable in isaac-p2jb).
• Install the isaac-foundation bb launcher into `libexec`; a thin `bin/isaac`
  wrapper invokes `bb` against it (isaac.main). This is exactly what p2jb built.
• Point `url`/`sha256` at isaac-foundation's published release tarball (tag
  v0.1.0 exists today; bump per release).
• `test do`: smoke — `isaac --version` (or `isaac help`) and `isaac init` in a
  sandbox prefix exit 0 and scaffold a config.

## Dependency strategy — (A) chosen

Vendor foundation base deps at `brew install`: `bb prepare` against
`libexec/isaac-foundation/libexec/bb.edn` with `HOME=libexec/deps-home`, then
the `bin/isaac` wrapper exports `CLJ_CONFIG` / `DEPS_CLJ_DIR` to that cellar
cache so `help` / `--version` / `init` work offline. Installed modules still
resolve on demand over the network (`isaac modules install`).

## Implementation (slagyr/homebrew-tap `11a10ec`)

• `Formula/isaac.rb` — v0.1.0 tarball, babashka dep, libexec launcher layout.
• `.github/workflows/tests.yml` — `brew install` + `brew test` all tap formulae.

## Relationships

• Capstone of the packaging story: p2jb (launcher, DONE) is what this wraps;
  pairs with isaac-dhzy / isaac-xdg3 (`isaac modules install` pulls published
  coords from the registry — network, regardless of (A)/(B)).
• Lives in slagyr/homebrew-tap (the tap already hosts braids.rb). This bean
  tracks the work; the .rb lands in that repo, not isaac.
• Needs a published foundation release tarball + sha256 (v0.1.0 present).

## Acceptance

• `brew install slagyr/tap/isaac` succeeds on a clean machine (babashka pulled
  as a dep).
• `isaac --version` / `isaac help` / `isaac init` work post-install (per the
  chosen dep strategy).
• `brew test isaac` (the formula's test block) passes in CI.
