---
# isaac-6q8c
title: 'Foundation pre-work: add :subcommands to the :cli berth'
status: completed
type: task
priority: normal
created_at: 2026-06-12T12:48:52Z
updated_at: 2026-06-29T17:13:32Z
parent: isaac-brth
---

Phase A step 2 of the isaac-foundation extraction (see isaac-brth reshaping
note). isaac.service.cli registers a command with :subcommands, which the
:cli berth schema (src/isaac-manifest.edn:9-18) does not support. Needed
before the service command can become a manifest :cli contribution.

- [x] Spec in spec/isaac/cli_spec.clj: register-cli-command! resolves a
      symbol-valued :subcommands into the command map (command-help's
      subcommand rendering keeps working).
- [x] Extend the :cli berth decl schema in src/isaac-manifest.edn with
      :subcommands {:type :symbol}.
- [x] Teach isaac.cli/register-cli-command! (src/isaac/cli.clj:186-216) to
      maybe-resolve it.
- [x] Scenario in features/module/cli_as_berth.feature.

## Acceptance

- bb spec and bb features green.

## Summary of Changes

Made the core `:cli` berth support symbol-valued `:subcommands`, the pre-work that lets `isaac.service.cli`s `service` command become a manifest `:cli` contribution.

- **src/isaac/cli.clj** — `register-cli-command!` now destructures `:subcommands` and resolves it (symbol → var-value via `maybe-resolve`, same idiom as `:option-spec`), assoc-ing it into the command map so `command-help` renders the subcommand list. Inline-vector values also pass through.
- **src/isaac-manifest.edn** — added `:subcommands {:type :symbol}` to the `:cli` berth entry schema so contributions can carry it (unspecified keys are otherwise dropped by schema).
- **spec/isaac/cli_spec.clj** — new `register-cli-command!` describe proving a symbol-valued `:subcommands` resolves and renders in help (TDD: red → green).
- **modules/isaac.cli.greeter** — fixture extended with a `subcommands` var + `:subcommands` manifest key.
- **features/module/cli_as_berth.feature** — new scenario: a `:cli` berth command resolves symbol-valued `:subcommands` into its `--help` end-to-end.

`bb spec` green (1856 examples, 0 failures). `bb features` green except one pre-existing flaky `--follow` timing test in `features/logs/cli.feature` (`With --follow, picks up entries appended after startup`) — unrelated to this change; passes in isolation (17/17). Worth a follow-up bean to de-flake (likely a timing/sleep race, a Pass-B smell).


## Completed

Goal met via `isaac.cli.api/subcommands` multimethods and manifest `:namespace`
(not symbol-valued `:subcommands` in the berth schema — that path was superseded).

- **isaac-manifest.edn** — `:isaac/cli` entries carry `:namespace`; schema documents
  optional `subcommands` via api.
- **isaac.cli.registry/command-help** — renders subcommands from `(api/subcommands id)`.
- **modules/marigold.cli.greeter** — fixture with `defmethod cli-api/subcommands :greet`.
- **features/module/cli_as_berth.feature** — subcommands scenario green (no @wip).
- **isaac.service.cli** — already implements `cli-api/subcommands :service`.

Verified: `bb spec` and `bb features` green on isaac-foundation.
