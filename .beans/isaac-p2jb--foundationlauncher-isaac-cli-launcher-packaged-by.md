---
# isaac-p2jb
title: 'foundation/launcher: isaac CLI launcher — compose classpath from config, boot foundation'
status: todo
type: feature
priority: normal
created_at: 2026-06-16T18:50:24Z
updated_at: 2026-06-18T15:30:42Z
---

The brew-installed isaac is a thin bb launcher (mirror slagyr/homebrew-tap's
braids formula: depends_on borkdude/brew/babashka + a bb wrapper in libexec).
It composes the runtime classpath from the user's configured :modules
(published coordinates) via babashka.deps/add-deps and boots foundation
(isaac.main).

## Scope — packaged only (dev-local DROPPED)

• Resolve config :modules (published git/mvn coords) -> classpath -> boot
foundation. That's it.
• DEV needs no launcher support: run `bb isaac ...` from inside a checkout
(uses the repo's :dev-local alias / bb.edn against local code). Confirmed:
`bb isaac help` runs isaac.main under bb and dispatches the full set.
• NO --describe. The launcher owns the RESOLUTION (config :modules -> module
set + sources) and the loading; that resolution is DISPLAYED by `isaac modules
list` (the dhzy command), which is also the inspection/dry-run view and the
test seam. One surface, not two.

## bb-compatibility: CONFIRMED (2026-06-16)

Pure-bb launcher viable, no JVM fallback. All 9 modules: bb.edn + CI-on-bb +
zero bb-hostile patterns; suites (incl. isaac-server http-kit) run under bb;
`bb isaac help` dispatches the assembled command set.

## Acceptance — features/module/modules_list.feature (@wip)

Scenarios approved with Micah 2026-06-16. Scenarios 1 & 2 exercise the
resolution underneath dhzy's `modules list --edn`; scenario 3 is p2jb's own
proof of dynamic loading.

1. A configured module is listed with its source — :modules {:marigold.bridge
   {:local/root ...}} -> modules.0 = {id, coord, status :ok}, exit 0.
2. A malformed module entry is flagged, not crashed — :modules {:marigold.broken
   "not-a-coordinate"} -> modules.0.status :invalid, exit 0.
3. (@slow, IN DoD — must pass) A configured module is loaded and contributes its
   command — a SUBPROCESS `isaac` boots with :modules {:marigold.cli.greeter ...}
   and `greet --help` works. Only way to prove dynamic classpath composition;
   1 & 2 only prove resolution.

## Work this bean pulls in

• New gherclj step: `the isaac launcher is run with "<args>"` — shells out to the
  real launcher (fresh classpath) vs the in-process `isaac is run with`.
• Fixture marigold.cli.greeter ALREADY EXISTS in isaac-foundation/modules
  (manifest contributes :isaac/cli greet) — no fixture work; this also unblocks
  6q8c.
• Un-@wip the feature once green.

## Relationships

• Pairs with isaac-dhzy: p2jb owns the resolution + loading; dhzy's `modules
  list` (+ modules.edn registry) is the view onto it. Same feature file backs
  scenarios 1 & 2.
• Part of the brew-packaging story (isaac.rb next to braids.rb in
  slagyr/homebrew-tap).
