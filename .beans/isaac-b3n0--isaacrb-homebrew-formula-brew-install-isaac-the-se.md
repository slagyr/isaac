---
# isaac-b3n0
title: 'isaac.rb: homebrew formula — brew install isaac (the seed)'
status: draft
type: task
created_at: 2026-06-18T16:48:52Z
updated_at: 2026-06-18T16:48:52Z
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

## Open decision — dependency strategy (DECIDE before implementing)

How do foundation's OWN runtime deps land?
• (A) Vendor at build time: `brew install` resolves foundation's base classpath
  into the cellar -> `isaac init`/help work OFFLINE; slower, reproducible install.
• (B) Resolve on first run: first `isaac` invocation composes the classpath via
  bb/tools.deps -> fast install, needs network on first use.
Recommendation: (A) for foundation's base deps (a brew tool should work offline
out of the box), while INSTALLED MODULES still resolve on demand over the network
— consistent with `isaac modules install` (isaac-dhzy), which is inherently a
network op. So: base offline-ready, modules network-resolved.

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
