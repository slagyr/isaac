---
# isaac-shr3
title: "Move config --sources flag to config sources subcommand"
status: completed
type: task
priority: low
created_at: 2026-04-18T23:08:21Z
updated_at: 2026-04-18T23:17:28Z
---

## Description

The `--sources` flag lists contributing config files — a distinct operation, not a print modifier. It doesn't belong alongside `--raw` and `--reveal` (which modify how the config prints). It belongs as a subcommand alongside `validate` and `get`.

## Change
- `isaac config --sources` → `isaac config sources`

## Updates
- src/isaac/cli/config.clj: remove `--sources` from option-spec, add "sources" subcommand branch in run
- features/cli/config.feature: rename scenario and change invocation to `config sources`
- Help text: move `sources` into the Subcommands list, remove from Options

## Structurally clean surface after change
- isaac config                — print
- isaac config --raw          — print modifier
- isaac config --reveal       — print modifier
- isaac config sources        — list contributing files  (new)
- isaac config validate       — validate
- isaac config get <path>     — get a value

## Acceptance Criteria

1. isaac config sources lists contributing files (no --sources flag anymore)
2. isaac config --sources either errors with 'unknown flag' or is removed cleanly
3. features/cli/config.feature scenario updated to use 'config sources'
4. help text shows sources in Subcommands, not Options
5. bb features passes
6. bb spec passes

