---
# isaac-y2dn
title: 'isaac-handbook: isaac handbook CLI — operator man pages for the POH'
status: in-progress
type: feature
priority: normal
tags:
    - handbook
created_at: 2026-10-08T21:15:43Z
updated_at: 2026-10-12T21:25:49Z
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

## Acceptance (features/cli.feature)

- [ ] Scenario "no args prints the TOC of installed chapters; a module with no handbook is absent (isaac-y2dn)":
  `bb features features/cli.feature:47`
- [ ] Scenario "a module-id argument prints that chapter (isaac-y2dn)":
  `bb features features/cli.feature:56`
- [ ] Scenario "a module#slug argument prints that section (isaac-y2dn)":
  `bb features features/cli.feature:65`
- [ ] Scenario "several topic ids print in order; an unknown one is listed unknown, known ones still print, exit 1 (isaac-y2dn)":
  `bb features features/cli.feature:74`
- [ ] Scenario "a chapter larger than handbook.max-chars still prints in full (isaac-y2dn)":
  `bb features features/cli.feature:84`
- [ ] Manifest `:isaac/cli :handbook` with `:hosted true`.
- [ ] `isaac help handbook` documents the command.
- [ ] This module's handbook chapter documents `isaac handbook`.
- [ ] Version bump; `bb spec` / `bb features` / `bb lint` green.

feature-baseline: isaac-handbook 410185cd8f2e687eeb7bc98b663ff9c5f106a2e6
feature-blob: isaac-handbook features/cli.feature aaff00c192f4f8aecc0451f130affd85da878c04 47,56,65,74,84
