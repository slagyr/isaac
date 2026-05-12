---
# isaac-soyn
title: "Use c3kit.apron.schema.path for config get instead of hand-rolled get-path"
status: completed
type: task
priority: low
created_at: 2026-04-19T01:01:15Z
updated_at: 2026-04-19T23:13:54Z
---

## Description

src/isaac/cli/config.clj:82-91 defines get-path — a hand-rolled dotted-path walker that splits on '.' and coerces numeric segments to vector indices. c3kit.apron.schema.path (shipped in apron 2.6.0) provides data-at with a richer, standardized grammar:

- Dot keyword: user.name
- Bracket index: points[0]
- Bracket string: crew["bill"]
- Bracket keyword: crew[:joe]
- Wildcard: crew[*] or crew.*

Replace our get-path with data-at. Benefits:

1. Standard JSONPath-ish bracket syntax out of the box
2. Wildcards unlock future queries like config get providers[*].api-key
3. Schema validation errors already use this grammar — aligns user-facing path syntax across validate errors and config get arguments

Also useful for isaac-9eht (schema pretty-print): data-at's counterpart schema-at navigates schemas, so isaac config schema crew or isaac config schema providers.anthropic becomes a one-liner.

## Scope
- Remove get-path in src/isaac/cli/config.clj
- Delegate isaac config get <path> to c3kit.apron.schema.path/data-at
- Update features/cli/config.feature scenarios to demonstrate bracket syntax at least once (e.g., config get crew["marvin"].soul or similar)
- Keep the existing dotted scenarios — data-at supports both grammars

## Open questions (worker surfaces before coding)
- Does data-at handle all our cases (map keys as keywords AND strings)?
- Behavior on a missing key — our current code returns nil and the CLI prints 'not found: <path>'. Preserve that.

## Acceptance Criteria

1. src/isaac/cli/config.clj uses c3kit.apron.schema.path/data-at; hand-rolled get-path removed
2. config get with dotted paths (existing scenarios) still passes
3. At least one scenario demonstrates bracket syntax — config get providers["grok"].api or similar
4. Behavior on missing key unchanged: exit 1, stderr 'not found: <path>'
5. bb features passes
6. bb spec passes

