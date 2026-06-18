---
# isaac-dhzy
title: 'foundation: ''isaac modules'' CLI subcommand — compose the assistant via config'
status: todo
type: feature
priority: normal
created_at: 2026-06-16T18:43:21Z
updated_at: 2026-06-18T16:26:19Z
---

Product vision: a brew-installed isaac (foundation) is the seed; the user
composes their assistant by INSTALLING MODULES. `isaac modules install <name>`
resolves via the registry and adds the module to the user's config :modules;
foundation's loader (discover! / berths / schema-compose) picks it up on the
next run.

## Command surface (a `modules` command on the :isaac/cli berth, declared by foundation)

• `isaac modules list`               — INSTALLED modules: id, source (coord),
  status. Local config only, no network. (Specced in module/modules_list.feature,
  shared with p2jb.)
• `isaac modules available [search]` — the CATALOG of installable modules,
  fetched from the registry.
• `isaac modules install <name>`     — resolve name -> :coord via registry,
  write the COORDINATE into config :modules. Confirm "Installed <name>".
• `isaac modules remove <name>`      — remove from config :modules. Confirm
  "Removed <name>".

## Registry + test seam

Catalog of official modules is EDN hosted IN THE ISAAC REPO
(github.com/slagyr/isaac/modules.edn), fetched via raw github (homebrew-tap
pattern). Default source is that raw-github URL; the `:module-registry` config
key OVERRIDES it with a path (relative to the Isaac root) or URL — the seam for
tests and private registries.

  ;; registry shape
  {:greeter {:coord {:git/url "..." :git/tag "v0.1.0"} :desc "..."} ...}

install/available fetch this; install looks up name -> :coord and writes :coord
to config :modules. Cache the fetched registry.

## Behavior — config management, NOT runtime

install/remove/list operate on CONFIG (:modules in config/isaac.edn); they do
not run module code. Reuse existing config-mutate machinery (isaac.config.mutate
/ cli set-value / nav). Foundation loads from :modules on the next invocation.
Pairs with epic isaac-iiga: an installed module LOADS as config presence; any
service it owns only STARTS when the server runs.

## Acceptance — features/module/modules.feature (@wip), 5 scenarios

Approved with Micah 2026-06-18. Fixtures point at marigold modules (no network,
no real modules). Errors -> stderr + exit 1; confirmations -> stdout + exit 0.
Config landing is asserted STRUCTURALLY on config/isaac.edn.

1. available --edn lists the catalog (sorted by id; id + desc).
2. install greeter -> "Installed greeter", and config/isaac.edn :modules gains
   {:greeter <coord>} (name resolved to coordinate).
3. remove greeter -> "Removed greeter", and config/isaac.edn :modules -> {}.
4. install <unknown> -> stderr "Unknown module: nope", exit 1, :modules
   unchanged ({}).
5. registry unavailable (:module-registry points at a missing file) -> stderr
   "Could not reach the module registry", exit 1, :modules unchanged.

## Steps — reuse existing; ONE new

Existing: `Isaac root <root> contains config:`, `the isaac file <path> exists
with:` (registry fixture), `isaac is run with`, `the stdout EDN contains:`, `the
stdout contains`, `the stderr contains`, `the exit code is`.
NEW (this bean adds): `the isaac file "<path>" EDN contains:` in cli_steps — an
UN-GATED, CLI-context, assert-only structural EDN file inspector (file analogue
of `the stdout EDN contains:`; reuse value-at-path). Distinct from the
scheduler-phase dual-mode `the EDN isaac file ... contains:` — see isaac-gyk1
(cleanup) to reconcile the two.

## Relationships

• Foundation owns this (:isaac/cli berth, config mutate, module loader).
• Pairs with isaac-p2jb (launcher reads :modules + composes the classpath;
  modules list is the view onto its resolution; shared feature modules_list).
• isaac-gyk1: split/clean the overlapping dual-mode EDN-file step.
• Registry hosted in isaac repo. Related: epic isaac-iiga (load/unload vs
  start/stop).

## Scope boundary (does NOT create the real registry)

dhzy owns: the `modules` command + the registry FORMAT + a TEST-FIXTURE registry
for its scenarios. It does NOT create/seed the real
github.com/slagyr/isaac/modules.edn — that is isaac-xdg3 (blocked by isaac-why8,
version-tagging the module repos). dhzy is buildable/testable against the
fixture without either.
