---
# isaac-xjgu
title: "Document global flags in top-level usage output"
status: completed
type: task
priority: low
created_at: 2026-04-22T21:44:09Z
updated_at: 2026-04-22T23:20:51Z
---

## Description

isaac.main/usage prints a 'Usage: isaac <command> [options]' header plus a commands list, but never documents the global flags. --home is processed by home/extract-home-flag but invisible to anyone running 'isaac --help'.

Scope:
- Extend usage output with a 'Global Options:' section listing --home (and any other globals that exist today)
- Include --help / -h too
- Keep the commands list

Example target output:

  Usage: isaac <command> [options]

  Global Options:
    --home <dir>    Override Isaac's home directory (default: ~/.isaac)
                    May also be set via ~/.config/isaac.edn or ~/.isaac.edn
    --help, -h      Show this message

  Commands:
    config    ...
    chat      ...
    ...

Future additions (other global flags as they emerge) land in the same section.

## Acceptance Criteria

1. 'isaac --help' output begins with 'Usage: isaac [options] <command> [args]'.
2. A 'Global Options:' section lists --home and --help/-h.
3. The existing 'Commands:' section appears after global options.
4. Remove @wip from the scenario in features/cli/usage.feature.
5. bb features features/cli/usage.feature passes.
6. bb features passes overall.
7. bb spec passes.

## Design

Implementation notes:
- Update isaac.main/usage to produce:

    Usage: isaac [options] <command> [args]

    Global Options:
      --home <dir>    Override Isaac's home directory (default: ~/.isaac)
                      May also be set via ~/.config/isaac.edn or ~/.isaac.edn
      --help, -h      Show this message

    Commands:
      <command>   <description>
      ...

- Flip the usage line from 'isaac <command> [options]' to 'isaac [options] <command> [args]' — matches Unix convention (git, docker, kubectl).
- Add a 'Global Options:' section between the usage line and the commands list.
- Format: use same two-column indent style the commands list uses for alignment.
- Home-pointer description references the mechanism from isaac-skul.
- Future global flags land in the same section as they're added.

## Notes

Documented top-level global options in isaac.main/usage, updated the usage line to 'isaac [options] <command> [args]', removed the @wip usage scenario, and aligned existing CLI feature expectations with the new top-level usage text. Verified with bb features features/cli/usage.feature features/cli/cli.feature, bb features, and bb spec in commits 0ad149e and 97a1f3d.

