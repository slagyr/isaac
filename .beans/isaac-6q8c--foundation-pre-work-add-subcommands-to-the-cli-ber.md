---
# isaac-6q8c
title: 'Foundation pre-work: add :subcommands to the :cli berth'
status: in-progress
type: task
priority: normal
created_at: 2026-06-12T12:48:52Z
updated_at: 2026-06-12T12:57:48Z
parent: isaac-brth
---

Phase A step 2 of the isaac-foundation extraction (see isaac-brth reshaping
note). isaac.service.cli registers a command with :subcommands, which the
:cli berth schema (src/isaac-manifest.edn:9-18) does not support. Needed
before the service command can become a manifest :cli contribution.

- [ ] Spec in spec/isaac/cli_spec.clj: register-cli-command! resolves a
      symbol-valued :subcommands into the command map (command-help's
      subcommand rendering keeps working).
- [ ] Extend the :cli berth decl schema in src/isaac-manifest.edn with
      :subcommands {:type :symbol}.
- [ ] Teach isaac.cli/register-cli-command! (src/isaac/cli.clj:186-216) to
      maybe-resolve it.
- [ ] Scenario in features/module/cli_as_berth.feature.

## Acceptance

- bb spec and bb features green.
