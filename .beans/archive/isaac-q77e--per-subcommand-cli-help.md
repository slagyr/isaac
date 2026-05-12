---
# isaac-q77e
title: "Per-subcommand CLI help"
status: completed
type: feature
priority: normal
created_at: 2026-04-21T15:58:24Z
updated_at: 2026-04-21T16:19:20Z
---

## Description

Today 'isaac help config' dumps every config subcommand's flags into one big page. Messy, hard to scan as subcommands grow. Each subcommand should have its own help page accessible two ways:

- 'isaac config <subcmd> --help'
- 'isaac config help <subcmd>'

First instance: config validate, which needs to document --as (data-path overlay) and the - stdin convention. Pattern applies to other config subcommands (get/set/unset/sources/reveal/schema) as follow-up once the mechanism exists.

See features/cli/config.feature for the 2 @wip scenarios.

## Acceptance Criteria

1. Implement --help short-circuit for 'isaac config validate'.
2. Implement 'isaac config help <subcommand>' routing.
3. Remove @wip from both scenarios in features/cli/config.feature.
4. bb features features/cli/config.feature passes (existing + 2 new).
5. bb features passes overall.
6. bb spec passes.

## Design

Implementation notes:
- CLI dispatch: recognize --help as a flag that short-circuits to per-subcommand usage.
- Recognize 'help <subcmd>' as an alias route (parsed before the subcommand's normal arg handling).
- Per-subcommand usage blocks live alongside the subcommand itself (isaac.config.cli.command or sibling). Top-level 'isaac help config' continues to work unchanged; subcommand-specific help is the new surface.
- Only scoped to config validate for this bead. Pattern can be replicated to get/set/unset/sources/reveal/schema incrementally — not required as part of this bead's acceptance.

