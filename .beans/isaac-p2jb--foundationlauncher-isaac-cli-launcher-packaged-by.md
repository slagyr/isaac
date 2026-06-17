---
# isaac-p2jb
title: 'foundation/launcher: isaac CLI launcher — compose classpath from config, boot foundation'
status: draft
type: feature
priority: normal
created_at: 2026-06-16T18:50:24Z
updated_at: 2026-06-17T15:49:08Z
---

The brew-installed `isaac` is a thin bb launcher (mirror slagyr/homebrew-tap's braids formula: depends_on
borkdude/brew/babashka + a bb wrapper in libexec). It composes the runtime classpath from the user's configured
:modules (published coordinates) via babashka.deps/add-deps and boots foundation (isaac.main).

## Scope — packaged only (dev-local DROPPED)
- Resolve config :modules (published git/mvn coords) -> classpath -> boot foundation. That's it.
- DEV needs no launcher support: run `bb isaac ...` from inside a checkout (uses the repo's :dev-local alias /
  bb.edn against local code). Confirmed: `bb isaac help` runs isaac.main under bb and dispatches the full set.
- NO `--describe`. The launcher owns the RESOLUTION (config :modules -> module set + sources) and the loading;
  that resolution is DISPLAYED by `isaac modules list` (the dhzy command), which is also the inspection/dry-run
  view and the test seam. One surface, not two.

## bb-compatibility: CONFIRMED (2026-06-16)
Pure-bb launcher viable, no JVM fallback. All 9 modules: bb.edn + CI-on-bb + zero bb-hostile patterns; suites
(incl. isaac-server http-kit) run under bb; `bb isaac help` dispatches the assembled command set.

## Acceptance (write @wip scenarios — assert via `isaac modules list`)
- No :modules configured -> `modules list` shows only foundation; a module's command is absent.
- :modules {isaac.agent <coord>} -> `modules list` shows isaac.agent with its source (e.g. git
  slagyr/isaac-agent v0.1.0).
- A malformed :modules entry -> `modules list` flags it (invalid coordinate) and does NOT crash; foundation's
  own commands still run.
- (lower priority, @slow) one real end-to-end load: a subprocess `isaac` run with a fixture-module coord proving
  the configured module's command actually works. (Only way to prove dynamic loading; the three above prove
  resolution.)

## Relationships
- Pairs with isaac-dhzy: p2jb owns the resolution + loading; dhzy's `modules list` is the view onto it.
- Part of the brew-packaging story (isaac.rb next to braids.rb in slagyr/homebrew-tap).
