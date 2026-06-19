---
# isaac-r7z5
title: isaac service install generates a broken plist (bb --config $HOME/bb.edn -m isaac.main)
status: completed
type: bug
priority: high
tags:
created_at: 2026-06-19T20:23:47Z
updated_at: 2026-06-19T20:57:15Z
---

`isaac service install` (and the monolith `isaac-dev service install`) write a
launchd plist that CANNOT START — it took down the zanebot assistant.

## What it generates

ProgramArguments:
  /usr/local/bin/bb --config /Users/<user>/bb.edn -m isaac.main server

Problems:
• --config points at $HOME/bb.edn, which is empty/absent -> isaac.main is not on
  the classpath -> crash loop: "Could not locate isaac/main.bb|clj|cljc on
  classpath" (FileNotFoundException), launchctl shows no PID / exit 1.
• Even with a valid bb.edn, the PACKAGED isaac should NOT invoke a raw
  `bb -m isaac.main` — it should run the packaged LAUNCHER (/usr/local/bin/isaac
  server), which composes the classpath from config :modules + bundled
  foundation. The raw-bb form is the dev-checkout pattern and doesn't apply to a
  brew install.

## Impact

Migrating the launchd service (com.slagyr.isaac) to the packaged isaac via
`isaac service install` produced a crash-looping service; the only working plist
was the hand-crafted monolith one (`bb --config .../isaac-live/bb.edn ...`). So
the deployment story (brew install -> run as a service) is currently broken.

## Fix

• Packaged `isaac service install`: generate ProgramArguments = the packaged
  launcher running `server` (e.g. /usr/local/bin/isaac server [--root <root>]),
  NOT `bb --config $HOME/bb.edn -m isaac.main`.
• Verify end-to-end: install -> start -> service boots, listens, and stays up
  (launchctl shows a PID); RunAtLoad/KeepAlive intact.
• Decide the dev (`isaac-dev`) form separately; it also wrote $HOME/bb.edn.

## Related (separate hazard, note)

The monolith server and the packaged foundation isaac SHARE ~/.isaac config.
Brew-side `modules upgrade`/`install` wrote foundation-era coords the monolith
can't load -> restarting the monolith now fails. Two incompatible isaacs on one
config root is its own deployment hazard (worth its own bean / part of the
migration plan).

## Relationships
• Blocks the "run packaged isaac as a service" deployment (iiga lifecycle).

## Handoff notes (work-3)

• `service install` prefers packaged launcher (`which isaac`) → plist runs
  `isaac [--root <root>] server`; dev checkout falls back to
  `bb --config <isaac-dir>/bb.edn -m isaac.main server`.
• `--root` on install (or global `--root`) forwarded to the launcher.
• Regression: `features/cli/service.feature` packaged + dev scenarios.

## Verification Notes

2026-06-19 verifier:

- Verified against fetched GitHub `isaac-server` `main` at `04aeda9`, not the stale local mirror.
- `env ISAAC_GIT=1 bb features features/cli/service.feature` passed: `16 examples, 0 failures, 63 assertions`.
- `env ISAAC_GIT=1 bb spec spec/isaac/service/cli_spec.clj spec/isaac/service/macos_spec.clj` passed: `31 examples, 0 failures, 63 assertions`.
