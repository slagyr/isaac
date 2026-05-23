---
# isaac-3cei
title: Module CLI commands must support --help (follow-up to isaac-vorl)
status: completed
type: feature
priority: normal
created_at: 2026-05-21T04:47:06Z
updated_at: 2026-05-22T04:05:41Z
---

## Motivation

Follow-up to [[isaac-vorl]]. The shipped `:cli` extension lets a module
register a top-level subcommand and surface it in the global help
listing, but the command's own `--help` and `isaac help <cmd>` paths
were never specified in scenarios. Other built-in CLIs all support
this convention (`config validate --help`, `config schema --help`,
`config set --help`).

Without these scenarios, future `:cli`-using modules will silently
ship without help text — a usability regression vs built-ins.

## Scenarios

Two `@wip` scenarios appended to `features/cli/module_cli.feature`:

- `features/cli/module_cli.feature:32` — module command has its own `--help` page
- `features/cli/module_cli.feature:45` — `isaac help <module-cmd>` reaches same page

Run with:
```
bb features features/cli/module_cli.feature
```

## Design call

The greeter manifest declares `:description` per CLI entry:
```edn
{:cli {:greet {:factory     isaac.cli.greeter/make-command
                :description "Print a greeting"}}}
```
and the factory return ALSO carries `:desc`. Two sources of truth.
The bean should pick one:

- **Manifest is canonical.** `register-module-command!` reads
  `:description` from the manifest entry and injects it as `:desc` in
  the registered command. Factory drops `:desc`. One declaration site;
  manifest stays the contract.
- **Factory wins.** Manifest `:description` is documentation-only;
  factory's `:desc` is what shows in help. Two sites, no enforcement.

Lean toward manifest-is-canonical.

## TODOs

- [ ] Decide manifest-canonical vs factory-canonical for `:description`.
- [ ] Plumb the chosen source through `register-module-command!` so
      `command-help` finds it.
- [ ] Update sample greeter to match (drop duplicate `:desc` if going
      manifest-canonical).
- [ ] Remove `@wip` tags from the two scenarios.

## Acceptance criteria

- Both `@wip` scenarios in `features/cli/module_cli.feature` pass.
- `isaac greet --help` exits 0, shows usage + manifest description.
- `isaac help greet` reaches the same page.
- `bb spec` and `bb features` green.

## Notes

Captured 2026-05-20. Sibling bean for the built-in service-help bug is
[[isaac-zgaj]] — same conceptual gap, different surface.
