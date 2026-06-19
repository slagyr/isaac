---
# isaac-xc9n
title: 'modules install <a> <b> ...: install ALL named modules (supersede reject)'
status: unverified
type: feature
tags:
    - unverified
created_at: 2026-06-19T16:41:52Z
updated_at: 2026-06-19T17:05:00Z
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
