---
# isaac-puhp
title: "Document the Gherkin table dialect (column/row markers, value parsing, # escapes)"
status: completed
type: task
priority: low
created_at: 2026-05-01T23:51:39Z
updated_at: 2026-05-04T23:32:05Z
---

## Description

The Gherkin table dialect used in this project has accumulated
several conventions and special behaviors, but they are only
documented inside step implementations. Agents (and humans) who
want to write feature scenarios cannot see the full surface of
what tables can express, leading to:

- Reinvention: proposing new steps for things tables already handle
- Drift: each new step file invents its own column conventions
- Slow review cycles: scenarios get rejected for using the wrong
  delete marker, missing a # prefix, etc.

Write one canonical reference. Treat existing step source as
authoritative; the doc captures and explains.

## What to cover

- Column conventions
  - "key" / "value" pair tables (config, edn-file)
  - "path" / "value" pair tables (assoc-in semantics)
  - dotted keys (comms.discord.token -> [:comms :discord :token])
  - "#comment" — ignored by matchers, used to annotate intent
  - "#index" — forces strict positional match in transcript steps
  - any other "#"-prefixed escapes
- Value parsing rules
  - JSON-array values: ["123","456"]
  - String literals: bare words, quoted strings
  - Numbers, booleans, keywords
  - regex cells: #"(?s)..."
  - "#"-prefixed sentinels (e.g. proposed #delete for slot removal)
  - When values are auto-EDN-parsed vs left as strings
- Row markers and special cells
  - Whatever "#index", "#comment" do at row vs cell level
  - Empty cells: meaning (often "no value" vs "delete")
- Step-specific dialect
  - "config:" vs "the isaac EDN file X exists with:" — same parser
    or each their own?
  - transcript matchers vs other matchers — column overrides

## Convention to add

"#" prefix is the established escape for special behavior. Add
"#delete" as the value marker meaning "remove this key from the
slice" — used by config-update steps that need to remove fields
or whole sub-trees.

## Where it lives

Options (pick during implementation):
- features/TABLES.md — sits next to feature files
- A subsection in CLAUDE.md — guaranteed-loaded for agents
- A SKILL.md under .claude/skills — discoverable by skill loader

Whatever the location, link it from CLAUDE.md so agents find it
on session start.

## Acceptance

- One doc exists at a known path covering all current dialect
  features.
- "#delete" convention added to step implementations that mutate
  config (config:, config is updated:, the isaac EDN file ...
  exists with:, etc.).
- A scenario in some existing or new feature exercises "#delete"
  end-to-end (likely added by the bead that needs it — e.g. P0.1
  reconciler stop scenario).
- CLAUDE.md links to the doc.
- Step implementations stay the source of truth; doc is a guide,
  not a parser spec.

## Out of scope

- Changing existing step phrases.
- Migrating existing scenarios to new conventions (write the doc
  first, migrate opportunistically).

