---
# isaac-b3n0
title: 'isaac.rb: homebrew formula — brew install isaac (the seed)'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-06-18T16:48:52Z
updated_at: 2026-06-18T17:18:17Z
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

- Verification failed on 2026-06-18. The formula works on a clean runner, but its smoke checks are not isolated from an existing Isaac home, so I returned the bean to plain `in-progress`.
- In [Formula/isaac.rb](../work-2/homebrew-tap/Formula/isaac.rb:17), the install phase sets `ENV["HOME"] = deps_home`, then runs `isaac help` and `isaac --version` at [lines 19-20](../work-2/homebrew-tap/Formula/isaac.rb:19). The test phase likewise runs `isaac help` / `isaac --version` without `--root` at [lines 38-39](../work-2/homebrew-tap/Formula/isaac.rb:38).
- That does not actually isolate Isaac root resolution, because foundation resolves the user root from `System.getProperty("user.home")`, not the shell `HOME` environment: see [isaac-foundation/src/isaac/config/root.clj](../isaac-foundation/src/isaac/config/root.clj:28) and [root.clj](../isaac-foundation/src/isaac/config/root.clj:76).
- On this machine, the real pointer file [~/.config/isaac.edn](/Users/micahmartin/.config/isaac.edn:1) points at a root whose config [config/isaac.edn](/Users/micahmartin/Projects/isaac/root/config/isaac.edn:1) declares local modules. Replaying the formula's launcher flow against that ambient home causes `isaac help` to try composing those modules and fail before the smoke check can pass.
- What is correct: the tap repo has the formula and workflow the bean asked for, and the clean-runner path likely explains the worker's CI-green note. The missing piece is explicit root/home isolation in the formula smoke commands so they remain deterministic on machines that already use Isaac.
