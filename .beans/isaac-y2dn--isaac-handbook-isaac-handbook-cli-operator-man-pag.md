---
# isaac-y2dn
title: 'isaac-handbook: isaac handbook CLI — operator man pages for the POH'
status: draft
type: feature
priority: normal
tags:
    - handbook
created_at: 2026-10-08T21:15:43Z
updated_at: 2026-10-08T21:15:43Z
---

## Why

Micah, 2026-10-08: handbook chapters are the POH — like man pages — but
they are only reachable through `handbook__read` (a crew tool). Operators
on the CLI have no way to read them. `isaac help` stays command usage
(flags, subcommands). The handbook is operating prose.

## Design (2026-10-08, Micah)

Dedicated `isaac handbook` command, wrapping the same topic index as
`handbook__read`. Not folded into `isaac help`.

- `isaac handbook` — table of contents (installed modules whose chapter
  resolves). A module with no `:handbook` contributes nothing.
- `isaac handbook isaac.gchat` — that chapter.
- `isaac handbook isaac.gchat#inbound-attachments` — one section.
- Several topic ids, printed in the order asked.
- Same topic ids as the tool (`<module-id>`, `<module-id>#<slug>`). No
  short aliases in v1.
- `:hosted true` so `skiff-isaac handbook …` works.
- Markdown to stdout. No pager.
- No size cap on the CLI. `handbook.max-chars` stays a tool/context
  limit.
- Unknown topic: still print the rest, exit 1. (The tool never fails
  the call; the CLI is an operator surface and should signal miss.)
- `handbook__configure` stays a tool. Operators already have
  `isaac config set` / `unset`.
- isaac-niqx generated inventory/config topics show up here later for
  free (same `read-topics` path). Not in this bean.

## Implementation sketch

isaac-handbook only:

- Manifest `:isaac/cli :handbook` (`:usage`, `:summary`, `:namespace`,
  `:hosted true`).
- Thin `isaac.handbook.cli`: `chapters/ordered-chapters` +
  `render/read-topics` with no max-chars (or a cap so large it never
  bites). Print the markdown. Exit 1 when any requested topic is
  unknown.
- This module's own handbook chapter documents the command.
- Version bump.

## Out of scope

`isaac help` topics. Short module-id aliases. A pager. Configure.
Generated reference topics (isaac-niqx). Changing `handbook__read`.

## Acceptance

Draft. Scenarios after the scenario plan is approved.

Likely home: isaac-handbook `features/cli.feature` (new file). Reuse the
marigold.charts / marigold.bridge fixtures from `features/read.feature`.
CLI steps already exist (`isaac is run with`, stdout contains, exit code).
