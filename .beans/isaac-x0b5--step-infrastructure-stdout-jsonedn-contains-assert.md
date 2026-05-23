---
# isaac-x0b5
title: 'Step infrastructure: stdout JSON/EDN contains assertions'
status: draft
type: feature
created_at: 2026-05-23T04:53:22Z
updated_at: 2026-05-23T04:53:22Z
---

## Motivation

Feature scenarios that assert against `--json` or `--edn` CLI output
currently use brittle substring/regex matches on the stringified
output. For structured assertions (e.g., "the third element's tags
include role/worker"), this gets noisy fast — order-paranoid
regexes, formatting fragility, no type checking.

Two new step definitions (plus one rendering guarantee) make
structured assertions clean.

## Scope

### `the stdout JSON contains:`

Asserts that walking `stdout-parsed-as-JSON` along each row's path
yields a value equal to the row's value-column-parsed-as-JSON-literal.

```gherkin
Then the stdout JSON contains:
  | path   | value                            |
  | 0.name | "joe"                            |
  | 0.tags | ["project/chess", "role/worker"] |
```

**Path semantics:**

- Dotted segments.
- Integer segment → array index (e.g., `0` accesses the first
  element).
- Bare segment → object key (string).

**Value semantics:**

- Each `value` cell is parsed as a JSON literal — strings need
  quotes (`"joe"`), numbers don't (`42`), arrays/objects use JSON
  syntax (`[1,2,3]`, `{"k":"v"}`), `null`/`true`/`false` as literal
  tokens.
- Equality comparison after parsing.

### `the stdout EDN contains:`

Symmetric for EDN output.

```gherkin
Then the stdout EDN contains:
  | path   | value                          |
  | 0.name | "joe"                          |
  | 0.tags | #{:role/worker :project/chess} |
```

Same path semantics. Each `value` cell parsed as an EDN literal —
sets, keywords, vectors all natural.

### Deterministic-render rule for JSON output

JSON has no native set type. When Isaac's CLI outputs JSON
containing a set field (`:tags`, future sets), the encoder renders
the set as a **sorted array** (alphabetical by string
representation). This makes path-equality assertions like
`0.tags = ["project/chess", "role/worker"]` deterministic.

EDN output renders sets as sets; equality is by value, no ordering
needed.

## Out of scope (deferred)

- **Membership assertions** for sets/arrays (e.g., "at path X, the
  set contains Y"). Equality-on-whole-value covers current cases;
  promote if a scenario demands membership without exact equality.
- **Wildcard paths** (e.g., `*.tags` to assert across all records).
  Add when a scenario calls for it.
- **Negation** (`does not contain` at a path). Existing `the stdout
  does not contain` (substring) covers the case adequately.

## Acceptance

- `the stdout JSON contains:` step lives in a stdio/CLI steps file
  (likely `cli_steps.clj`); implementation parses stdout as JSON,
  walks each row's path, asserts equality with parsed value.
- `the stdout EDN contains:` parallel implementation for EDN.
- Both steps emit clear failure messages: the offending path, the
  expected value, the actual value at that path.
- Both steps reject malformed input with a usable diagnostic
  ("stdout was not valid JSON: <head of stdout>").
- Set-typed fields rendered to JSON output use sorted arrays —
  verified by a unit/spec test on the JSON encoder.

## Relationship to other beans

- **Required by isaac-wr7d (Tagging).** Crew listing/show
  `--json`/`--edn` scenarios and session listing/show `--json`
  scenarios use these steps.
- **Likely used by isaac-ugx7 (Hail) and isaac-wirv (Session
  mutation).** Future feature scenarios for those beans will
  benefit from the same structured assertions.
