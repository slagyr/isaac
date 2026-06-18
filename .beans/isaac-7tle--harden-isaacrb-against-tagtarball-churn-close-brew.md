---
# isaac-7tle
title: Harden isaac.rb against tag/tarball churn + close brew-install verification gap
status: draft
type: task
created_at: 2026-06-18T17:52:12Z
updated_at: 2026-06-18T17:52:12Z
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
