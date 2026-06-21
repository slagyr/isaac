---
# isaac-smmm
title: isaac modules deps — emit JVM launch classpath/deps from config modules
status: draft
type: feature
created_at: 2026-06-21T01:08:42Z
updated_at: 2026-06-21T01:08:42Z
parent: isaac-5zfv
---

Foundation command (`isaac.modules.cli`) that emits the dependency set needed to
launch the server on the JVM, derived from the current root's config. First piece
of epic isaac-5zfv; blocks `server --runtime` and `service install --runtime`.

## Behavior
- `isaac modules deps --classpath` — print the fully-resolved classpath string:
  foundation seed `:paths` + each config `:module` coord (resolved via gitlibs)
  + injected `org.clojure/clojure` (bb has it built-in; `-Scp` on the JVM needs
  it explicitly). This is what the launch embeds.
- `isaac modules deps --edn` — print the `-Sdeps` map (foundation `:paths`,
  module coords, 92p3 seed-authoritative `:exclusions
  [io.github.slagyr/isaac-foundation]`). For inspection / clojure-resolves use.
- Default (no flag): `--edn`. (Decide: or require an explicit flag.)

## Implementation
- Reuse the loader's existing resolution — `module-loader/compose-config-modules!`
  already computes this coord set for bb's dynamic classpath. Factor out the pure
  "compute coords + exclusions + foundation seed" core so BOTH the bb path
  (add-classpath) and this command (emit) share one function. No duplicate logic.
- The `--runtime jvm` trampoline (child 2) calls that function in-process; this
  command is the standalone / plist-facing / human-debuggable exposure.

## Scenarios (DRAFT — pending Micah review; do not generate feature file yet)
```gherkin
Scenario: --classpath emits a launchable classpath
  Given an Isaac root configured with modules :isaac.agent and :isaac.server
  When isaac is run with "modules deps --classpath"
  Then the stdout contains the foundation src path
  And  the stdout contains a resolved path for each configured module
  And  the stdout contains an "org.clojure/clojure" entry
  And  the exit code is 0

Scenario: --edn emits a -Sdeps map with seed-authoritative exclusions
  When isaac is run with "modules deps --edn"
  Then the stdout EDN at path deps.io.github.slagyr/isaac-agent.exclusions
       contains :io.github.slagyr/isaac-foundation
  And  the stdout EDN paths contains the foundation seed src

@slow
Scenario: the generated classpath actually boots isaac on the JVM
  When isaac is run with "modules deps --classpath" and the output is passed to
       "clojure -Scp <cp> -M -m isaac.main --version"
  Then the stdout contains the isaac version
```

## Acceptance
- `--classpath` output is directly usable as `clojure -Scp "<out>" -M -m
  isaac.main server` (proven in the spike).
- `--edn` carries the foundation exclusion on every module dep.
- Generation reuses the loader coords (no second resolution implementation).
