---
# isaac-3d8j
title: crew command becomes a management command with explicit list and richer show output
status: todo
type: feature
priority: normal
created_at: 2026-06-25T15:30:02Z
updated_at: 2026-06-25T15:30:02Z
---

`isaac crew` currently mixes two different shapes: bare `crew` acts like a listing command, while `show` hangs off the side with list-style rendering, truncated soul text, and no subcommand help. The zanebot session on 2026-06-25 surfaced the gaps directly:

- `isaac crew show --help` is treated as a missing crew id
- `isaac crew` / `isaac crew --help` do not clearly present `list` + `show` as a management surface
- `crew show` human output is a horizontal table instead of detail view
- `crew show --edn` / `--json` expose `:soul-source` and `:tags-text` instead of a faithful full `:soul`

## Decisions locked (2026-06-25, Micah)

- `isaac crew` is a management command.
- Bare `isaac crew` shows help by default.
- `isaac crew list` is the explicit listing subcommand.
- `isaac crew --help` shows management help and lists `list` + `show`.
- `isaac crew show --help` has its own subcommand help.
- `crew show` human output becomes key/value detail and includes the full soul content.
- Machine-readable `crew show` output uses full `:soul` and removes presentation-only fields `:soul-source` and `:tags-text`.
- Reused fallout in the tag feature should move listing/filter invocations from `crew ...` to `crew list ...`.
- No new step defs are needed for these scenarios.

## Feature selectors

Committed `@wip` scenarios live in `isaac-agent`:

- `isaac-agent/features/crew/cli.feature:14`
- `isaac-agent/features/crew/cli.feature:22`
- `isaac-agent/features/crew/cli.feature:29`
- `isaac-agent/features/crew/cli.feature:36`
- `isaac-agent/features/crew/cli.feature:51`
- `isaac-agent/features/crew/cli.feature:60`
- `isaac-agent/features/crew/cli.feature:75`
- `isaac-agent/features/tagging/crew_tags.feature:20`
- `isaac-agent/features/tagging/crew_tags.feature:33`
- `isaac-agent/features/tagging/crew_tags.feature:47`
- `isaac-agent/features/tagging/crew_tags.feature:61`
- `isaac-agent/features/tagging/crew_tags.feature:77`
- `isaac-agent/features/tagging/crew_tags.feature:93`
- `isaac-agent/features/tagging/crew_tags.feature:109`
- `isaac-agent/features/tagging/crew_tags.feature:130`
- `isaac-agent/features/tagging/crew_tags.feature:145`
- `isaac-agent/features/tagging/crew_tags.feature:173`

## Acceptance

Run in `isaac-agent`:

```bash
cd /Users/micahmartin/agents/plan/isaac-agent
bb features features/crew/cli.feature features/tagging/crew_tags.feature
```

Targeted selectors if needed:

```bash
cd /Users/micahmartin/agents/plan/isaac-agent
bb features \
  features/crew/cli.feature:14 \
  features/crew/cli.feature:22 \
  features/crew/cli.feature:29 \
  features/crew/cli.feature:36 \
  features/crew/cli.feature:51 \
  features/crew/cli.feature:60 \
  features/crew/cli.feature:75 \
  features/tagging/crew_tags.feature:20 \
  features/tagging/crew_tags.feature:33 \
  features/tagging/crew_tags.feature:47 \
  features/tagging/crew_tags.feature:61 \
  features/tagging/crew_tags.feature:77 \
  features/tagging/crew_tags.feature:93 \
  features/tagging/crew_tags.feature:109 \
  features/tagging/crew_tags.feature:130 \
  features/tagging/crew_tags.feature:145 \
  features/tagging/crew_tags.feature:173
```

Definition of done:

- remove `@wip` from the scenarios above
- `isaac crew` help/list/show behavior matches the locked scenarios
- `crew show --edn` / `--json` expose full `:soul` and no presentation-only fields
