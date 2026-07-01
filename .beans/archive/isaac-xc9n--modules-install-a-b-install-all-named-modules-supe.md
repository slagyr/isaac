---
# isaac-xc9n
title: 'modules install <a> <b> ...: install ALL named modules (supersede reject)'
status: completed
type: feature
tags: []
created_at: 2026-06-19T16:41:52Z
updated_at: 2026-06-19T16:26:10Z
---

Micah 2026-06-19: `isaac modules install a b c` should install ALL named
modules. SUPERSEDES isaac-iy94's choice to reject >1 ("one module name at a
time").

## Change

run-install (src/isaac/modules/cli.clj) currently errors when (> (count names)
1). Instead: resolve EVERY name first (all-or-nothing — if ANY name is unknown
or invalid, error and write NOTHING), then write them all into :modules in a
single whole-map mutate, and confirm each. Single-name still works.

## Scenarios (add to features/module/modules.feature; update the reject scenario)

  Scenario: install adds every named module
    Given Isaac root "/tmp/isaac" contains config:
      """
      {:module-registry "registry.edn"}
      """
    And the isaac file "registry.edn" exists with:
      """
      {:alpha {:coord {:local/root "modules/alpha"} :desc "A"}
       :beta  {:coord {:local/root "modules/beta"}  :desc "B"}}
      """
    When isaac is run with "modules install alpha beta"
    Then the stdout contains "Installed alpha"
    And the stdout contains "Installed beta"
    And the exit code is 0
    And the isaac file "config/isaac.edn" EDN contains:
      | path          | value                         |
      | modules.alpha | {:local/root "modules/alpha"} |
      | modules.beta  | {:local/root "modules/beta"}  |

  Scenario: a list with an unknown module writes nothing (all-or-nothing)
    Given Isaac root "/tmp/isaac" contains config:
      """
      {:modules {} :module-registry "registry.edn"}
      """
    And the isaac file "registry.edn" exists with:
      """
      {:alpha {:coord {:local/root "modules/alpha"} :desc "A"}}
      """
    When isaac is run with "modules install alpha nope"
    Then the stderr contains "Unknown module: nope"
    And the exit code is 1
    And the isaac file "config/isaac.edn" EDN contains:
      | path    | value |
      | modules | {}    |

## Relationships

• Supersedes the reject-one-at-a-time behavior from isaac-iy94.
• Should be IN the v0.1.2 release.

## Verification notes

- Verification passed on 2026-06-19 against fetched GitHub `isaac-foundation` `main` at `305c337`, not the stale local mirror.
- The `modules.feature` edit is in-bounds for the bean: it replaces the old reject-one-at-a-time scenario with the requested multi-install and all-or-nothing scenarios, without weakening the remaining coverage.
- Focused acceptance proof passed: `env ISAAC_GIT=1 bb features features/module/modules.feature` → `7 examples, 0 failures, 22 assertions`.
- Full repo lane also passed on this head: `ISAAC_GIT=1 bb ci` → `754 spec examples, 0 failures` and `106 feature examples, 0 failures`.
- The delivered behavior is present in [src/isaac/modules/cli.clj](/Users/micahmartin/agents/verify/isaac-foundation/src/isaac/modules/cli.clj:219): install now resolves all names first, writes one merged `:modules` map, and prints one confirmation per installed module. The registry cache reset in [spec/isaac/foundation/root_steps.clj](/Users/micahmartin/agents/verify/isaac-foundation/spec/isaac/foundation/root_steps.clj:15) is exercised by the green feature run.
