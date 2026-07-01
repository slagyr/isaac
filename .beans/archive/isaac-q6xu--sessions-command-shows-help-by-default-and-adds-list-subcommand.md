---
# isaac-q6xu
title: sessions command shows help by default and adds list subcommand
status: completed
type: feature
priority: normal
created_at: 2026-06-25T14:57:58Z
updated_at: 2026-06-25T15:39:30Z
---

We just settled the CLI convention for management commands: the top-level command shows management help by default, and concrete actions hang off explicit subcommands. `isaac crew` is moving to that shape. `isaac sessions` still does the older thing: bare `isaac sessions` executes the listing behavior directly.

This bean tracks bringing `sessions` into the same convention.

## Intended behavior

- `isaac sessions` shows help by default
- `isaac sessions --help` shows management help, including subcommands
- `isaac sessions list` becomes the explicit listing command
- existing listing/filter behavior moves under `sessions list`
- existing subcommands (`show`, `set`, `unset`, `delete`) remain, and `list` joins them

## Why this exists

- The current CLI is inconsistent:
  - `isaac config` and `isaac service` show help by default
  - `isaac sessions` executes a default action instead
- The `crew` command is being reshaped into a real management surface; `sessions` should follow the same convention rather than remain an odd one-off.

## Likely feature impact

- Existing scenarios in `isaac-agent/features/session/cli.feature` that currently run `isaac sessions` for listing will need to move to `isaac sessions list`.
- New scenarios will be needed for:
  - bare `sessions` showing help
  - `sessions --help` listing `list` alongside `show`, `set`, `unset`, `delete`

## Design notes

- This is a command-shape change, not a behavior rewrite of session listing itself.
- Machine-readable output for `sessions list --json/--edn` should remain what the listing already returns today; the change is how users reach that behavior.
- The `@wip` feature scenarios are now committed; remaining work is implementation and verification against those scenarios.

## Related

- Companion planning work is in progress for the `crew` command to follow the same management-command convention.

## Scenarios committed (2026-06-25)

The `@wip` scenarios now live in `isaac-agent/features/session/cli.feature`:

- `isaac-agent/features/session/cli.feature:17`
- `isaac-agent/features/session/cli.feature:24`
- `isaac-agent/features/session/cli.feature:33`
- `isaac-agent/features/session/cli.feature:51`
- `isaac-agent/features/session/cli.feature:64`
- `isaac-agent/features/session/cli.feature:70`
- `isaac-agent/features/session/cli.feature:77`
- `isaac-agent/features/session/cli.feature:124`
- `isaac-agent/features/session/cli.feature:135`
- `isaac-agent/features/session/cli.feature:146`

These cover:

- bare `isaac sessions` showing help
- `sessions --help` listing subcommands
- explicit `sessions list` for the existing listing/filter/color surfaces

## Acceptance

Run in `isaac-agent`:

```bash
cd /Users/micahmartin/agents/plan/isaac-agent
bb features features/session/cli.feature
```

Targeted selectors if needed:

```bash
cd /Users/micahmartin/agents/plan/isaac-agent
bb features \
  features/session/cli.feature:17 \
  features/session/cli.feature:24 \
  features/session/cli.feature:33 \
  features/session/cli.feature:51 \
  features/session/cli.feature:64 \
  features/session/cli.feature:70 \
  features/session/cli.feature:77 \
  features/session/cli.feature:124 \
  features/session/cli.feature:135 \
  features/session/cli.feature:146
```
