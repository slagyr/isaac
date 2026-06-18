---
# isaac-dhzy
title: 'foundation: ''isaac modules'' CLI subcommand — compose the assistant via config'
status: draft
type: feature
priority: normal
created_at: 2026-06-16T18:43:21Z
updated_at: 2026-06-18T13:43:18Z
---

Product vision: a brew-installed `isaac` (foundation) is the seed; the user composes their assistant by
INSTALLING MODULES. `isaac modules install <name>` resolves <name> via the registry and adds the module to the
user's config :modules; foundation's loader (discover! / berths / schema-compose) picks it up on the next run.

## Command surface (a `modules` command on the :isaac/cli berth, declared by foundation)
- `isaac modules list`               — INSTALLED modules: each configured module's id, source (coord/path),
                                        status (resolves?/loaded?). LOCAL config only, no network. Also the
                                        inspection/dry-run view AND the launcher (p2jb) resolution test seam.
- `isaac modules available` [search] — the CATALOG of installable modules, FETCHED from the registry (below).
- `isaac modules install <name>`     — fetch registry, resolve <name> -> coordinate, write it into config :modules.
- `isaac modules remove <name>`      — remove <name> from config :modules.

## Registry (resolves the name -> coordinate question)
The catalog of official modules is a simple EDN hosted IN THE ISAAC REPO (which persists as the beans/docs host —
now also the module registry), fetched via raw github (same pattern as a homebrew tap):

  ;; github.com/slagyr/isaac/modules.edn
  {:agent   {:coord {:git/url \"https://github.com/slagyr/isaac-agent.git\" :git/tag \"v0.1.0\"}
             :desc  \"Crew, LLM providers, sessions, drive/bridge, tools\"}
   :discord {:coord {:git/url \"...\" :git/tag \"...\"} :desc \"Discord comm\"}
   ...}

`install` and `available` fetch this; `install` looks up :<name> -> :coord and writes it to config :modules.
Cache the fetched registry; allow an override URL (testing / private registries).

## Behavior — config management, NOT runtime
install/remove/list operate on CONFIG (:modules in isaac.edn); they do not run module code. Reuse the existing
config-mutate machinery (isaac.config.mutate / cli set-value / nav). Foundation loads from :modules on the next
invocation. Pairs with epic isaac-iiga: an installed module LOADS as config presence; any service it owns only
STARTS when the server runs (installing discord doesn't spin up a client).

## Acceptance (write @wip scenarios)
- `isaac modules available` (against a test registry) lists the catalog entries (name + desc).
- `isaac modules install agent` resolves agent via the registry and adds its coordinate to config :modules;
  `isaac modules list` then shows it.
- `isaac modules remove agent` removes it from config; `list` no longer shows it.
- `isaac modules install <unknown>` -> friendly structured error (not in registry); config unchanged.
- registry fetch failure -> clear error; config unchanged.

## Relationships
- Foundation owns this (the :isaac/cli berth, config mutate, the module loader).
- Pairs with isaac-p2jb (launcher reads :modules + composes the classpath; `modules list` is the view onto its resolution).
- Registry hosted in the isaac repo (modules.edn). Related: epic isaac-iiga (load/unload vs start/stop).
