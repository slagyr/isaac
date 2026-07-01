---
# isaac-b3n0
title: 'isaac.rb: homebrew formula — brew install isaac (the seed)'
status: completed
type: task
priority: normal
tags: []
created_at: 2026-06-18T16:48:52Z
updated_at: 2026-06-18T18:29:00Z
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

## Implementation (slagyr/homebrew-tap `37e8c5e`)

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

## Verification notes

- Verified on 2026-06-18 after pulling current `main` in `isaac` and `homebrew-tap`.
- The isolation fix is present in [Formula/isaac.rb](../work-2/homebrew-tap/Formula/isaac.rb:17): install-time smoke commands now pass `--root` to an empty builder-owned root, and the formula `test do` uses the same isolated `--root` path for `help` and `--version`.
- I replayed the formula's install and post-install smoke sequence locally with the real ambient Isaac pointer files still present on this machine. The updated flow passed: `bb prepare`, `isaac --root <smoke-root> help`, `isaac --root <smoke-root> --version`, and `isaac --root <new-root> init` all exited 0 and scaffolded `config/isaac.edn`.
- I did not run a direct `brew install slagyr/tap/isaac` in this verifier session because Homebrew's tap-trust gate blocks that path without changing local trust settings. The formula logic itself, including the previously failing ambient-home case, now checks out.
