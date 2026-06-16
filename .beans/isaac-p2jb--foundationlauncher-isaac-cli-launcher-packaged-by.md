---
# isaac-p2jb
title: 'foundation/launcher: isaac CLI launcher — compose classpath from config, boot foundation'
status: draft
type: feature
priority: normal
created_at: 2026-06-16T18:50:24Z
updated_at: 2026-06-16T21:10:08Z
---

The brew-installed `isaac` is a thin bb launcher (mirror slagyr/homebrew-tap's braids formula: depends_on
borkdude/brew/babashka + a bb wrapper in libexec). It composes the runtime classpath from the user's configured
:modules (published coordinates) via babashka.deps/add-deps and boots foundation (isaac.main).

## Scope — packaged only (dev-local DROPPED)
- Resolve config :modules (published git/mvn coords) -> classpath -> boot foundation. That's it.
- DEV needs no launcher support: run `bb isaac ...` from inside a checkout (foundation/agent/...), which uses the
  repo's existing :dev-local alias / bb.edn against local code. (Confirmed: `bb isaac help` already runs
  isaac.main under bb and dispatches the full assembled command set.)

## bb-compatibility: CONFIRMED (2026-06-16)
Pure-bb launcher viable, no JVM fallback. All 9 modules: bb.edn + CI-on-bb + zero bb-hostile patterns; test
suites (incl. isaac-server http-kit) run under bb; `bb isaac help` dispatches the assembled command set.

## Acceptance (write @wip scenarios)
- No :modules configured -> only foundation's own commands; a module command is absent.
- :modules {agent <coord>} -> agent's berths/CLI commands become available (config -> loaded, end-to-end).
- A bad/unresolvable coord in :modules -> structured error naming the module; foundation's own commands still
  work (one bad module must not brick the CLI).
- (optional) `isaac --describe` prints each module -> resolved source without loading (debug aid + test seam).

## Relationships
- Pairs with isaac-dhzy (the `modules` command writes :modules; launcher reads it).
- Part of the brew-packaging story (isaac.rb next to braids.rb in slagyr/homebrew-tap).
