---
# isaac-hzp1
title: sessions/crew --help omit their subcommands (set/unset/show/delete, show)
status: completed
type: bug
priority: normal
created_at: 2026-06-22T22:07:19Z
updated_at: 2026-06-22T22:30:13Z
---

`isaac sessions --help` shows only the list options — it never lists the `show`/`set`/`unset`/`delete` subcommands, so the entire session-update surface is undiscoverable. `isaac crew --help` has the same gap (hides `show`).

## Audit (which commands are affected)
The help renderer `registry/command-help` (foundation) ALREADY lists subcommands when a command declares them via the `cli-api/subcommands` multimethod or a `:subcommands` key (registry.clj:55-67). Surveyed every command CLI:
- OK (surface their subcommands): `service` (cli-api/subcommands), `config` + `modules` (manual :pre-sections), `auth` + `hail` (manual help-text listing).
- BROKEN (dispatch subcommands inline in run-fn, declare none -> help hides them):
  - **sessions** (`isaac.session.cli`): show, set, unset, delete
  - **crew** (`isaac.crew.cli`): show

## Fix
Implement `cli-api/subcommands` for `:sessions` and `:crew` (same as `isaac.service.cli` does) so `command-help` auto-renders them. e.g.:
```clojure
(defmethod cli-api/subcommands :sessions [_id]
  [{:name "show"   :summary "Show one session"}
   {:name "set"    :summary "Set a mutable field: sessions set <id>.<path> <value>"}
   {:name "unset"  :summary "Clear a mutable field: sessions unset <id>.<path>"}
   {:name "delete" :summary "Delete a session"}])
(defmethod cli-api/subcommands :crew [_id]
  [{:name "show" :summary "Show one crew member"}])
```
This is additive (subcommands multimethod feeds help rendering only; dispatch stays in run-fn). For set/unset, the summary should surface the `<id>.<path> <value>` usage so the update affordance is discoverable. Optional: also refactor run-fn dispatch to read from the declared subcommand list (DRY), as service does.

## Scenarios (DRAFT — pending Micah review; do not generate feature files yet)
Go in EXISTING `isaac-agent/features/session/cli.feature` and `features/crew/cli.feature`. Reuse existing steps (`isaac is run with`, `the stdout matches:`, `the exit code is 0`) — NO new step defs.

## Acceptance
- `isaac sessions --help` lists show/set/unset/delete with summaries (set/unset show the `<id>.<path> <value>` form).
- `isaac crew --help` lists show.
- Existing list-option help still renders.
- Feature coverage added to session/cli.feature + crew/cli.feature.

## Notes
Same family as 6zll (service install --help option summaries), shipped this deploy. Surfaced 2026-06-22 when Micah asked whether sessions could update a session — it can (sessions set/unset), but help hid it.

## Scenarios (locked 2026-06-22, Micah-approved)

Add to EXISTING `isaac-agent/features/session/cli.feature` and `features/crew/cli.feature`. Reuse existing steps (`isaac is run with`, `the stdout matches:`, `the exit code is 0`) — NO new step defs.

```gherkin
  # session/cli.feature
  Scenario: sessions --help lists its subcommands
    When isaac is run with "sessions --help"
    Then the stdout matches:
      | pattern                                          |
      | Usage: isaac sessions                            |
      | Subcommands:                                     |
      | show\s+Show one session                          |
      | set\s+Set a mutable field.*<id>\.<path> <value>  |
      | unset\s+Clear a mutable field.*<id>\.<path>      |
      | delete\s+Delete a session                        |
    And the exit code is 0

  # crew/cli.feature
  Scenario: crew --help lists its subcommands
    When isaac is run with "crew --help"
    Then the stdout matches:
      | pattern              |
      | Usage: isaac crew    |
      | Subcommands:         |
      | show\s+Show one crew |
    And the exit code is 0
```
The set/unset patterns assert the `<id>.<path> <value>` usage text so the update affordance is provably discoverable. Summary wording in these scenarios is the contract for the `cli-api/subcommands` :summary strings.



## Worker notes (work-2)

Added `cli-api/subcommands` for `:sessions` (show/set/unset/delete) and `:crew` (show). Help rendering via existing `registry/command-help` — dispatch unchanged in run-fn.

Agent: b0a6150
Feature scenarios added to session/cli.feature + crew/cli.feature (16 examples green).
