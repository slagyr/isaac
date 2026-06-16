---
# isaac-dhzy
title: 'foundation: ''isaac modules'' CLI subcommand — compose the assistant via config'
status: draft
type: feature
priority: normal
created_at: 2026-06-16T18:43:21Z
updated_at: 2026-06-16T18:47:47Z
---

Part of the product vision: a brew-installed `isaac` (foundation) is the seed; the user composes their
assistant by INSTALLING MODULES. `isaac modules install <name>` adds the module to the user's config :modules;
foundation's existing loader (discover! / berths / schema-compose) picks it up on the next run.

## Command surface (a `modules` command on the :isaac/cli berth, declared by foundation)
- `isaac modules list`              — show configured modules + load status.
- `isaac modules install <name>`    — resolve <name> -> coordinate, add to config :modules, fetch/cache.
- `isaac modules remove <name>`     — remove from config :modules.
- (maybe) `isaac modules available`/`search` — list installable modules from the registry.

## Behavior — config management, NOT runtime
install/remove MUTATE THE CONFIG (:modules map in isaac.edn) — they do not run module code. Reuse the existing
config-mutate machinery (isaac.config.mutate / cli set-value / nav). Foundation loads from :modules on the next
invocation (existing contract). Pairs with epic isaac-iiga: an installed module LOADS as config presence; any
service it owns only STARTS when the server runs (so installing discord doesn't spin up a client).

## Open design (cross-ref the product-vision discussion)
- Name -> coordinate resolution: a small registry (name -> git/mvn coord) for the homebrew-like UX, vs raw git
  coords (slagyr/isaac-<x>) as the zero-infra start. DECIDE.
- Runtime loading: a brew-installed `isaac` must build its CLASSPATH from the configured modules at run time.
  LIKELY a BB-LAUNCHER mirroring the slagyr/homebrew-tap braids formula (depends_on borkdude/brew/babashka; a
  bb wrapper in libexec) — and use babashka.deps/add-deps to resolve the user's :modules coords dynamically.
  Lighter than a JVM launcher and fits Isaac's bb-first design. CONFIRM all modules run under bb (ISAAC.md:
  bb-first, fix bb breakage upstream); else a JVM fallback for that module. `modules install` writes config +
  ensures the dep is resolvable; the launcher composes the classpath. Separate bean.

## Acceptance (write @wip scenarios; promote to todo after)
- `isaac modules install agent` adds agent's coordinate to config :modules; `isaac modules list` shows it;
  the next `isaac` run loads it (its berths/schema appear).
- `isaac modules remove agent` removes it; next run does not load it.
- installing an unknown module -> a friendly structured error, config unchanged.

## Relationships
- Foundation owns this (it owns the :isaac/cli berth, config mutate, the module loader).
- Depends on: the launcher (classpath-from-config) + the brew packaging.
- Related: epic isaac-iiga (load/unload vs start/stop).
