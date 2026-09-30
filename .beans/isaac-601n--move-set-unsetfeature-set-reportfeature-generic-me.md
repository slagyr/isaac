---
# isaac-601n
title: Move set_unset.feature + set_report.feature generic mechanics to isaac-foundation; check schema_cli_options.feature for redundancy
status: draft
type: task
priority: normal
created_at: 2026-09-30T02:43:45Z
updated_at: 2026-09-30T02:43:45Z
---

## Ruling

Micah, 2026-09-29: isaac-agent's `features/config/` holds ~100 scenarios
for foundation-owned config-command behavior that should move to
foundation with Marigold fixtures; agent keeps only the scenarios that
test agent's own schema.

## Problem

`isaac-agent/features/config/set_unset.feature` (26 scenarios) and
`set_report.feature` (4 scenarios) mostly test isaac-foundation's own
`config set`/`config unset` mechanics (scalar writes, idempotency, type
coercion, `--force`, `--edn`, set-member removal, confirmation output) —
plus the remaining portion of `cli.feature` not covered by
cleanup-bean-7 (the `set`/`unset`/`keys`/`list` scenarios, lines 584-1131).
`schema_cli_options.feature` (7 scenarios) needs a redundancy check against
foundation's own fixture-based `config_schema.feature` before deciding
move vs. delete, same as cleanup-bean-7's Group E.

## Mapping — set_unset.feature (26 scenarios)

MOVE (generic set/unset mechanics — Marigold-ize with a fixture schema
that has a scalar field, a set-typed field, a keyword field, and an entity
table, mirroring what `config_set_namespaced.feature`/
`config_set_undeclared_key.feature` already use):

- "scalar set writes a value at a known map path"
- "scalar unset removes a value at a known map path"
- "config set is idempotent when the value is already present"
- "config unset is idempotent when the value is absent"
- "config set errors on a path the schema doesn't recognize" — CHECK
  overlap with foundation's `config_set_undeclared_key.feature`; likely
  redundant, prefer delete over duplicate
- "config set errors when the value doesn't match the schema type"
- "config set defaults.tools.max-lines succeeds and the value lands" —
  agent-specific PATH (`defaults.tools.max-lines`) but generic BEHAVIOR;
  Marigold-ize with an equivalent fixture field
- "config set conforms a bare name to the keyword set the field holds"
- "config set conforms a keyword to a one-member set instead of crashing"
- "config set conforms a comma list to a set of keywords"
- "config set keeps digits a string when the field is a string"
- "config unset with a member removes only that member"
- "config unset refuses a value on a path that is not a set"
- "config set still accepts a reference to an entity that is not defined yet"
- "config set confirms what it wrote and where"
- "config set confirms a set member it added"
- "config unset confirms what it removed"
- "config set --edn prints only the structured record"
- "config set --help documents the set-member path form"
- "config set on a crew using a module-contributed session policy succeeds"
  — borderline; the MECHANISM (set succeeding against a module-registered
  `[:registered-in?]` field) is generic and may already be covered by
  `config_set_undeclared_key.feature`'s or `config_schema.feature`'s
  registered-in fixture; CHECK, else STAYS (it's exercised through agent's
  real session-policy berth, which is agent-specific context even if the
  underlying mechanism is generic)

STAYS (isaac-dnib explicitly pins these as regression scenarios for its
`:required?`→`:required` cutover — **do not touch, move, or delete any of
these; confirm they're still present and green in isaac-agent after dnib
lands, that's the whole point of them staying**):

- "setting the first of two required fields is refused and hints at --force"
- "--force writes the first required field and the second set validates clean"
- "--force still refuses a value that does not parse"
- "config unset --force removes a required field and warns"
- "the stdin map form sets both required fields in one write with no warning"
- "config set --help documents --force and the stdin-map form"

## Mapping — set_report.feature (4 scenarios)

STAYS (all four are about a "broad directory grant" warning — this is
agent's crew tool-directory-allow concept, not a generic foundation
warning-reporting mechanism):

- "warnings elsewhere in the config collapse to a count after the confirmation"
- "a refused set leads with the error, then the warning count"
- "config validate still reports an unacknowledged broad directory grant"
- "an acknowledged broad directory grant is never reported"

CHECK: is the underlying "warnings collapse to a count after confirmation"
UI behavior itself generic (i.e., does it apply to ANY warning, not just
the directory-grant one)? If so, consider a generic version with a
Marigold-fixture-triggered warning instead of a real crew-tool-grant one —
but only if a worker confirms the collapsing logic lives in foundation's
own `mutate.clj`/`cli/set.clj` reporting code and isn't itself
agent-specific. Flagging, not asserting — did not trace this one deeply
this session.

## Mapping — cli.feature (this bean's remaining portion: set/unset/keys/list)

MOVE (generic; "comm field" scenarios Marigold-ize with a fixture
dynamic-schema field mirroring what `config_set_dotenv.feature`'s
`marigold.p4oj.discord` fixture already does for a conditionally-required
namespaced field):

- "set writes a new crew member to isaac.edn by default"
- "set writes to the existing entity file when one already defines the key"
- "set edits the frontmatter of an entity that lives in <id>.md"
- "unset removes a frontmatter field from an entity that lives in <id>.md"
- "set writes to isaac.edn when the entity is already defined there"
- "set writes new entities to entity files when prefer-entity-files is true"
- "set writes soul to the companion .md when it already exists"
- "set creates a companion .md when a new soul exceeds 64 characters"
- "set writes short soul inline in the entity file"
- "set refuses to write a value that fails type validation"
- "set errors on a path the schema does not recognize" — dedupe against
  set_unset.feature's identical-titled scenario and
  config_set_undeclared_key.feature
- "set on a module-provided comm field does not warn unknown key"
- "set on an unknown comm field still warns via the loader"
- "set warns when the comm's module is not declared"
- "set warns when the comm has no :type yet"
- "unset removes a key from the file where it lives"
- "unset that empties an entity file deletes it"
- "set writes a whole entity read from stdin"
- "set replaces an existing entity rather than merging"
- "config help lists set and unset subcommands"
- "config set --help documents stdin form and examples"
- "config keys prints bare key names at a path"
- "config keys with no path lists root keys"
- "config list prints keys with their config source"
- "config list with no path lists root keys and sources"
- "a leaf path prints nothing"
- "keys and list emit structured output under --json"
- "config validate --json emits structured warnings"

## Mapping — schema_cli_options.feature (7 scenarios)

LIKELY ALREADY COVERED — DELETE AS REDUNDANT, DO NOT MOVE (same call as
cleanup-bean-7's Group E; foundation's `config_schema.feature` fixture
scenarios — especially "a [:registered-in?] field lists the berth's
registered entries as options, excluding non-configurable ones" — look
like a direct generic replacement for this whole file):

- "comm slot :type lists user-configurable comm kinds from manifests"
- "config schema renders manifest-supplied comm fields with provenance prefix"
- "config schema comms.value renders every manifest-supplied field inline"
- "config schema comms.value with no modules shows only base fields"
- "config schema for a manifest-supplied field errors when the module isn't declared"
- "config schema renders manifest-supplied provider fields with provenance prefix"
- "config schema renders the statically-declared tool config fields"

Worker must diff scenario bodies (not just titles) before deleting — this
planning session did not read schema_cli_options.feature's full body,
only its scenario titles and foundation's config_schema.feature header
comment (which explicitly says it replaced exactly this kind of
comm-specific rendering logic, per isaac-3y69).

## Acceptance

- Foundation gains new feature coverage for the MOVE lists above.
- isaac-agent's set_unset.feature shrinks to the dnib-pinned STAYS list
  only; set_report.feature and schema_cli_options.feature are either
  fully STAYS (set_report) or fully deleted-as-redundant
  (schema_cli_options, pending the diff).
- isaac-dnib's six pinned set_unset.feature scenarios are untouched and
  green.
- Full suites green on both repos.

## Likely repo scope

`isaac-foundation` (new feature file(s)), `isaac-agent` (trim
set_unset.feature to dnib's pinned list, trim/delete cli.feature's
remaining portion, decide schema_cli_options.feature's fate after diff).

## Notes

- **This bean must land after isaac-dnib** for the set_unset.feature
  portion specifically — dnib's Acceptance section explicitly names four
  set_unset.feature scenario titles it depends on staying green
  (isaac-dnib bean body, "isaac-agent scenarios that must stay green").
  Confirm dnib is `completed` before touching this file at all.
- Split from cleanup-bean-7 only because set_unset.feature/cli.feature's
  set-portion is large enough to be its own worker session — no other
  reason; land in either order relative to bean-7.

## Open questions

- Same as cleanup-bean-7: confirm the "likely redundant" calls
  (schema_cli_options.feature, the two `config set errors on a path the
  schema doesn't recognize` duplicates) with real scenario-body diffs,
  not just title matching.
- set_report.feature's "warnings collapse to a count" — generic mechanism
  or agent-specific? Flagged, not resolved, above.
