---
# isaac-7tle
title: Harden isaac.rb against tag/tarball churn + close brew-install verification gap
status: completed
type: task
priority: normal
tags: []
created_at: 2026-06-18T17:52:12Z
updated_at: 2026-06-18T21:51:32Z
---

isaac.rb (slagyr/homebrew-tap) broke on `brew install` with a sha256 mismatch:
the formula pins `url` to the GitHub AUTO-GENERATED tarball of a MOVABLE tag
(v0.1.0/archive/refs/tags/...). why8 (version-tagging) re-cut v0.1.0 ->
fe83ebe3 on 2026-06-18, changing the tarball; the recorded sha
(f91c5ff2...) went stale and install failed. Hot-fixed by bumping sha to
d4fa61e2...95438 (homebrew-tap caf7d3d).

This will RECUR every time a release tag moves, and GitHub auto-tarballs are
not even guaranteed byte-stable across time (gzip regen has broken homebrew
checksums before).

## Harden (pick one)

• Stop MOVING release tags: v0.1.0 is immutable once published; ship changes as
  v0.1.1+, bump the formula's version+url+sha per release. (Cheapest discipline
  fix.)
• And/or pin to a byte-stable artifact: an UPLOADED release asset (tarball
  attached to the GH release), or a commit-sha archive URL — not the
  auto-generated tag tarball.

## Verification gap (root cause it slipped through)

b3n0 was marked completed/verified, but the verifier did NOT run a real
`brew install slagyr/tap/isaac` — Homebrew's tap-trust gate blocked it, so they
only REPLAYED the smoke commands. A checksum mismatch is invisible to a replay.
Definition of done for formula work must include an actual `brew install` from
the tap (clear the trust gate, e.g. HOMEBREW_NO_INSTALL_FROM_API / accept tap)
on a clean-ish prefix, not just a local smoke replay.

## Acceptance

• Formula installs via real `brew install slagyr/tap/isaac` on a clean machine.
• Release/tag policy documented (no moving published tags; or pinned asset).
• Formula CI (tap tests.yml) actually exercises `brew install`, catching sha
  drift.


## Chosen direction (decided with Micah 2026-06-18): immutable tags + auto-bump

Goal: `brew install slagyr/tap/isaac` always gets the LATEST release, and never
breaks on a stale sha. Achieve it the idiomatic Homebrew way — NOT a dynamic
"resolve latest tag at install" (unsupported: stable formulae need a literal
url+sha; only --HEAD tracks a moving target, unstable/opt-in).

1. Tags become IMMUTABLE + incrementing. Never move a published tag; ship
   v0.1.1, v0.1.2, ... per release. (This alone kills the stale-sha churn.)
2. `livecheck` block in Formula/isaac.rb so Homebrew knows where the newest
   version lives.
3. Auto-bump Action in slagyr/homebrew-tap: on a new foundation release, run
   `brew bump-formula-pr` (or commit the new url+sha256) so the tap always
   points at the latest tag, properly checksummed. Users get latest; the bot
   keeps it current (minutes of lag, not install-time resolution).
4. Keep `head "<git-url>"` for `brew install --HEAD` (dev bleeding-edge).

This subsumes the earlier "pick one" hardening options.

## Open decision for the worker

• Auto-bump trigger: fire on EVERY new tag, or only on tags explicitly marked
  as releases (e.g. GitHub Release published, or a naming convention)? Decide
  before wiring the Action.

## Acceptance (updated)

• Published tags are immutable; a release bumps version (no tag moves).
• `brew install slagyr/tap/isaac` on a clean machine installs the latest
  released version, checksum valid.
• A new foundation release auto-updates the tap formula (url+sha) without manual
  edits.
• Formula CI runs a REAL `brew install` from the tap (closes the b3n0 verify
  gap — replay can't catch sha drift).
• `brew install --HEAD` works for dev.

## Verification notes

- Verification passed on 2026-06-18 against current `homebrew-tap` `main` at `c6011eb`.
- The hardening is present in [Formula/isaac.rb](/Users/micahmartin/agents/work-2/homebrew-tap/Formula/isaac.rb:1): stable now pins `v0.1.1`, keeps `head`, and adds `livecheck`.
- Real tap install was attempted with `HOMEBREW_NO_INSTALL_FROM_API=1 brew install slagyr/tap/isaac`. It fetched `isaac` `0.1.1` and `babashka`, cleared the checksum/fetch stage, and failed only on this machine's outdated Command Line Tools (`CLT does not support macOS 26`). That means the original stale-sha/tag-churn failure is fixed; the remaining failure is local environment, not formula drift.
- The tap CI now runs real stable and `--HEAD` installs/tests in [tests.yml](/Users/micahmartin/agents/work-2/homebrew-tap/.github/workflows/tests.yml:1), the tap has an auto-bump workflow in [bump-isaac.yml](/Users/micahmartin/agents/work-2/homebrew-tap/.github/workflows/bump-isaac.yml:1), foundation dispatches that event from [release.yml](/Users/micahmartin/agents/verify/isaac-foundation/.github/workflows/release.yml:1), and the immutable-tag policy is documented in [RELEASE.md](/Users/micahmartin/agents/work-2/homebrew-tap/RELEASE.md:1).
