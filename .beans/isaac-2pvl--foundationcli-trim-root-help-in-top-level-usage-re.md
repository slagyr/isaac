---
# isaac-2pvl
title: 'foundation/cli: trim --root help in top-level usage; relocate source-precedence to ''config sources'''
status: todo
type: feature
priority: normal
tags:
    - in-progress
    - unverified
created_at: 2026-06-18T18:07:34Z
updated_at: 2026-06-18T21:16:12Z
---

The top-level `isaac` usage spends two lines on --root, one of which dumps the
full config-source precedence — reference detail that 99% of users never need at
the front door.

  --root <dir>    Override Isaac's root directory (default: ~/.isaac)
  --help, -h      May also be set via ISAAC_ROOT, ~/.config/isaac.edn, or ~/.isaac.edn

## Change

In src/isaac/main.clj `usage()` (lines 62-75), collapse --root to ONE line and
drop the "May also be set via ..." line:

  --root <dir>    Isaac root directory (default: ~/.isaac)
  --help, -h      Show this message

Relocate the precedence detail to where it belongs — `isaac config sources`
(already exists: src/isaac/config/cli/sources.clj; precedence documented in
src/isaac/config/root.clj:13). Verify `config sources` (and/or `isaac config
--help`) surfaces the full resolution order: --root flag > ISAAC_ROOT >
~/.config/isaac.edn > ~/.isaac.edn > default ~/.isaac. ADD it there if missing.
Keep the front door clean — no pointer line needed (discoverable via `config`).

## Acceptance (feature-test in features/cli)

• `isaac` / `isaac --help` top-level usage does NOT contain "May also be set"
  and shows --root on a single line with the default.
• `isaac config sources` lists the full precedence order (flag, env, the two
  config files, and the default).
• No behavior change to root resolution itself — display only.

## Relationships

• Pairs with the richer-help bean (the hidden `help` command); that bean can
  ALSO expose this detail as `isaac help root`. Complementary, not blocking.
