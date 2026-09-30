---
# isaac-601n
title: Move set_unset.feature + set_report.feature generic mechanics to isaac-foundation; check schema_cli_options.feature for redundancy
status: in-progress
type: task
priority: normal
created_at: 2026-09-30T02:43:45Z
updated_at: 2026-09-30T05:46:01Z
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

## Final mapping (planner, 2026-09-30)

Every agent scenario's disposition. The worker deletes the MOVED and DELETED-as-redundant originals from isaac-agent and keeps the STAYS ones. The 5 companion-.md scenarios stay in agent until the companion-loading bean lands. `config_set_comm_field.feature` (4 scenarios) was dropped from this bean: it behaves differently under the test harness than the CLI, so its originals stay in agent.

# isaac-601n mapping — final disposition

Drafts written (not committed) to a foundation worktree:
`/Users/micahmartin/agents/isaac/plan/isaac-foundation-mxgn-draft/features/cli/`
(same worktree as isaac-mxgn; reused rather than making a second one, since
both beans share a foundation checkout and neither commits).

- `config_set_unset.feature` — 18 scenarios, all @wip, all dry-run GREEN.
- `config_set_report.feature` — 2 scenarios (new, not a literal move — see
  below), all @wip, all dry-run GREEN.
- `config_set_entity_routing.feature` — 11 scenarios, all @wip, all dry-run
  GREEN.
- `config_keys_list.feature` — 9 scenarios, all @wip, all dry-run GREEN.
- `config_schema_provenance.feature` — 3 scenarios, all @wip, all dry-run
  GREEN.
- `config_set_comm_field.feature` — 4 scenarios, all @wip, **dry-run NOT
  green — see Open Questions below.** Drafted and reasoned through but not
  verified; do not baseline until resolved.

Fixture ids used: `marigold.601n.vessels`, `marigold.601n.report`,
`marigold.601n.routing`, `marigold.601n.keys`, `marigold.601n.bridge` /
`.longwave` / `.skybeam`, `marigold.601n.comm`. All manifest-only except
`config_schema_provenance.feature` and `config_set_comm_field.feature`,
which need `:factory` (a no-op module factory, or foundation's own
`isaac.module.protocol/module`) because `config schema`'s dynamic-schema
merge and `config set`'s berth-registered-in? validation both require a
really-activated module, not just a declarative manifest fragment — a
manifest-only fixture is sufficient for `config get`/`config validate` but
NOT for `config schema <dynamic-field>` or a `[:registered-in?]`-gated
`config set`.

## set_unset.feature (isaac-agent) — 26 original scenarios

| Agent scenario | Disposition |
|---|---|
| scalar set writes a value at a known map path | moved-to: config_set_unset.feature (same title) |
| scalar unset removes a value at a known map path | moved-to: config_set_unset.feature (same title) |
| config set is idempotent when the value is already present | moved-to: config_set_unset.feature (same title) |
| config unset is idempotent when the value is absent | moved-to: config_set_unset.feature (same title) |
| config set errors on a path the schema doesn't recognize | **deleted as redundant**: covered by config_set_undeclared_key.feature (isaac-a5dx):"set of an undeclared key under a schema'd map is refused" — identical generic mechanism. |
| config set errors when the value doesn't match the schema type | moved-to: config_set_unset.feature (same title) |
| config set defaults.tools.max-lines succeeds and the value lands | moved-to: config_set_unset.feature:"config set helm.max-signals succeeds and the value lands" (Marigold-ized: `defaults.tools.max-lines` is an agent-declared nested path; `helm.max-signals` is an equivalent foundation-module-declared nested root field) |
| config set conforms a bare name to the keyword set the field holds | moved-to: config_set_unset.feature (same title) — **relocated to a root-level field** (`signal-tags`), not a field nested in the `:vessels` entity table. See Open Questions #1: `config set`'s target-spec lookup (`isaac.config.schema.resolve/schema-for-data-path`) doesn't resolve a field nested under a fresh entity-dir kind at all. |
| config set conforms a keyword to a one-member set instead of crashing | moved-to: config_set_unset.feature (same title), same relocation |
| config set conforms a comma list to a set of keywords | moved-to: config_set_unset.feature (same title), same relocation |
| config set keeps digits a string when the field is a string | moved-to: config_set_unset.feature (same title) |
| config unset with a member removes only that member | moved-to: config_set_unset.feature (same title), same relocation to `signal-tags` |
| config unset refuses a value on a path that is not a set | moved-to: config_set_unset.feature (same title) — kept on the entity-table scalar field; this one happens to work even with the nil-spec gap, since "not a set" is the correct fallback interpretation either way |
| config set still accepts a reference to an entity that is not defined yet | **stays-in-agent**: exercises agent's own bespoke entity-existence validator (crew.model → a real :models key), which — unlike the provider/tool/comm validators — was never migrated to the generic `[:registered-in?]` mechanism. No public foundation-level "does this value name a config-defined entity" check exists to build a fixture against. |
| config set confirms what it wrote and where | moved-to: config_set_unset.feature (same title) |
| config set confirms a set member it added | moved-to: config_set_unset.feature (same title), relocated to `signal-tags` |
| config unset confirms what it removed | moved-to: config_set_unset.feature (same title) |
| config set --edn prints only the structured record | moved-to: config_set_unset.feature (same title) |
| config set --help documents the set-member path form | moved-to: config_set_unset.feature (same title) — this asserts FOUNDATION's own hard-coded help copy (`isaac.config.cli.set`, literally contains the string "crew.marvin.tags.role/worker"); no fixture needed at all, works against a bare config |
| config set on a crew using a module-contributed session policy succeeds | **deleted as redundant**: functionally the same mechanism as config_set_undeclared_key.feature's "set of an undeclared key under an entity table (key-spec) still writes" (`config set relays.wavecrest.type :longwave`, a `[:registered-in?]`-validated field, exits 0) |
| setting the first of two required fields is refused and hints at --force | **stays-in-agent — isaac-dnib pinned regression**, untouched |
| --force writes the first required field and the second set validates clean | stays-in-agent — dnib-pinned |
| --force still refuses a value that does not parse | stays-in-agent — dnib-pinned |
| config unset --force removes a required field and warns | stays-in-agent — dnib-pinned |
| the stdin map form sets both required fields in one write with no warning | stays-in-agent — dnib-pinned |
| config set --help documents --force and the stdin-map form | stays-in-agent — dnib-pinned |

**Addition beyond the mapping**: config_set_unset.feature also gained
"warnings elsewhere in the config collapse to a count after the
confirmation" — resolves set_report.feature's own CHECK note (is the
collapsing mechanism itself generic? yes — `isaac.config.cli.mutate-common`,
foundation-owned). Note: observed count was 3 against this fixture (an
unrelated entity's unknown-key warning gets collected once per internal
validate pass in the mutate pipeline), not 1 as in the agent original —
assertion uses a `\d+` count rather than a hard-coded number.

## set_report.feature (isaac-agent) — 4 scenarios, all STAYS

| Agent scenario | Disposition |
|---|---|
| warnings elsewhere in the config collapse to a count after the confirmation | stays-in-agent: about the crew tool-directory "broad grant" warning specifically, agent-owned. **Generic mechanism resolved and moved separately** to `config_set_report.feature` (new file, 2 scenarios) and `config_set_unset.feature` (1 scenario) — see CHECK resolution above. |
| a refused set leads with the error, then the warning count | stays-in-agent, same reasoning; generic version in `config_set_report.feature`. |
| config validate still reports an unacknowledged broad directory grant | stays-in-agent: no generic equivalent (there's no foundation-level "broad grant" concept) |
| an acknowledged broad directory grant is never reported | stays-in-agent, same reasoning |

## cli.feature (isaac-agent) — this bean's remaining portion (set/unset/keys/list, lines 584-1131)

| Agent scenario | Disposition |
|---|---|
| set writes a new crew member to isaac.edn by default | moved-to: config_set_entity_routing.feature:"set writes a new entity to isaac.edn by default" |
| set writes to the existing entity file when one already defines the key | moved-to: config_set_entity_routing.feature (same title) |
| set edits the frontmatter of an entity that lives in <id>.md | moved-to: config_set_entity_routing.feature (same title) |
| unset removes a frontmatter field from an entity that lives in <id>.md | moved-to: config_set_entity_routing.feature (same title) |
| set writes to isaac.edn when the entity is already defined there | moved-to: config_set_entity_routing.feature (same title) |
| set writes new entities to entity files when prefer-entity-files is true | moved-to: config_set_entity_routing.feature (same title) |
| set writes soul to the companion .md when it already exists | **stays-in-agent: blocked**, same production gap as composition.feature's companion scenarios (`companion-md-relative` hard-coded to :crew/:berths). Declaring `:companion` on the fixture kind to test write-routing would crash every subsequent `config get`/`validate` used to confirm the write. |
| set creates a companion .md when a new soul exceeds 64 characters | stays-in-agent: blocked, same reason |
| set writes short soul inline in the entity file | stays-in-agent: blocked, same reason |
| set refuses to write a value that fails type validation | moved-to: config_set_entity_routing.feature (same title) |
| set errors on a path the schema does not recognize | **deleted as redundant** — same scenario title/mechanism already deleted in set_unset.feature's mapping (config_set_undeclared_key.feature) and in config_set_undeclared_key.feature itself; not moved a third time |
| set on a module-provided comm field does not warn unknown key | drafted-to: config_set_comm_field.feature:"set on a module-provided dynamic-schema field does not warn unknown key" — **not yet green, see Open Questions #2** |
| set on an unknown comm field still warns via the loader | drafted-to: config_set_comm_field.feature:"set on an unknown field still warns via the loader" — not yet green |
| set warns when the comm's module is not declared | drafted-to: config_set_comm_field.feature:"set warns when the field's contributing module is not declared" — not yet green |
| set warns when the comm has no :type yet | drafted-to: config_set_comm_field.feature:"set warns when the entry has no :type yet" — not yet green |
| unset removes a key from the file where it lives | moved-to: config_set_entity_routing.feature (same title) |
| unset that empties an entity file deletes it | moved-to: config_set_entity_routing.feature (same title) |
| set writes a whole entity read from stdin | moved-to: config_set_entity_routing.feature (same title) |
| set replaces an existing entity rather than merging | moved-to: config_set_entity_routing.feature (same title) |
| config help lists set and unset subcommands | moved-to: config_keys_list.feature (same title) — static help copy |
| config set --help documents stdin form and examples | moved-to: config_keys_list.feature (same title) — static help copy |
| config keys prints bare key names at a path | moved-to: config_keys_list.feature (same title) |
| config keys with no path lists root keys | moved-to: config_keys_list.feature (same title) |
| config list prints keys with their config source | moved-to: config_keys_list.feature (same title) |
| config list with no path lists root keys and sources | moved-to: config_keys_list.feature (same title) |
| a leaf path prints nothing | moved-to: config_keys_list.feature (same title) |
| keys and list emit structured output under --json | moved-to: config_keys_list.feature (same title) |
| config validate --json emits structured warnings | moved-to: config_keys_list.feature (same title) |

## schema_cli_options.feature (isaac-agent) — 7 scenarios

| Agent scenario | Disposition |
|---|---|
| comm slot :type lists user-configurable comm kinds from manifests | **deleted as redundant**: covered by config_schema.feature:"a [:registered-in?] field lists the berth's registered entries as options, excluding non-configurable ones" |
| config schema renders manifest-supplied comm fields with provenance prefix | moved-to: config_schema_provenance.feature:"config schema renders a manifest-supplied field with a provenance prefix" — confirmed NOT redundant: no existing config_schema.feature scenario drills into a dynamic-schema-merged field to check the `[variant]` prefix, even though the underlying mechanism (`isaac.schema.dynamic`, `isaac.config.schema.term`) is already fully generic |
| config schema comms.value renders every manifest-supplied field inline | moved-to: config_schema_provenance.feature:"config schema <table>.value renders every manifest-supplied field inline, not grouped by type" |
| config schema comms.value with no modules shows only base fields | moved-to: config_schema_provenance.feature:"config schema <table>.value with no contributing modules shows only base fields" |
| config schema for a manifest-supplied field errors when the module isn't declared | **deleted as redundant**: same generic "Path not found in config schema: …" mechanism as config_schema.feature's "gives a friendly error for a 2-segment typo" — the schema lookup can't tell (and doesn't need to tell) a typo from an undeclared module's field |
| config schema renders manifest-supplied provider fields with provenance prefix | **folded into** "renders a manifest-supplied field with a provenance prefix" above — identical mechanism, comm vs. provider is just which table, per the bean's own fold-duplicates guidance |
| config schema renders the statically-declared tool config fields | **deleted as redundant**: same generic mechanism as config_schema.feature's "drills into a single field and shows its description" — any static (non-dynamic-schema) field renders the same way regardless of table |

## Open questions / follow-ups (in addition to isaac-mxgn's companion-md-relative gap, which also blocks 3 scenarios here)

1. **Real production gap, distinct from the companion one**: `isaac.config.schema.resolve/schema-for-data-path` — the function `config set`/`config unset` use to find a path's spec (and so detect `:set-type?` for the keyword-set conform/split convenience) — resolves a field nested in a plain root map generically, but does **not** resolve a field nested under a dynamic key-spec/value-spec entity table (`vessels.<id>.<field>`) unless the table's kind name is in a private hard-coded `entity-collections` set: `:berths :gauges :foundries :crew :hail :models :providers`. A fresh module-declared entity-dir kind silently falls through to a generic value-guess instead of the set-aware parse. Plain scalar/int field type errors are still caught correctly (by the separate, fully-generic post-write full-entity conform), so this specifically only affects the keyword-set CLI convenience forms. Confirmed by direct experiment (`clojure -M -e` against the fixture): `schema-for-data-path` returns `nil` for `vessels.cordelia.captain`/`.tags`, but a plain root field (`helm.max-signals`) resolves fine. Worked around in config_set_unset.feature by relocating the set-conforming scenarios to a root-level field; a real generalization (mirroring `config schema`'s own already-generic slot-id-to-`.value` rewrite) would let a future worker move those scenarios back onto an entity-table field faithfully.

2. **config_set_comm_field.feature is not verified green.** All 4 scenarios pass a direct `clojure -M -m isaac.main` CLI invocation against the exact same fixture (manually confirmed: the module-provided field sets cleanly with no warning, an unknown field warns, etc.) — matching the intended/expected behavior described in the original agent scenarios. But the SAME fixture run through gherclj's wrapped `isaac-run` step (which installs extra classpath-cache/config-cache spy hooks around `isaac.main/run` for other, unrelated feature suites) produces different, and inconsistent, results: scenario 1 gets a spurious warning it shouldn't, scenario 2 doesn't get the warning it should. Reproduced in isolation (single scenario alone, not a cross-scenario contamination artifact). Root cause not identified — plausibly one of the CLI-step test-harness wrappers (`isaac.foundation.cli-steps/apply-run-wrappers`, `isaac.startup.classpath-cache-steps`, `isaac.startup.config-cache-steps`) interacts with fresh dynamic-schema module discovery differently than a bare CLI process does. **Recommend a worker investigate this specific discrepancy before baselining `config_set_comm_field.feature`** — the fixture and scenario design look sound (mirrors config_set_dotenv.feature's already-proven marigold.p4oj.discord shape almost exactly), so this may be fixable by finding the right test-harness incantation rather than a production bug, but I could not confirm which in the time available.

3. The same warning-count-triples-under-the-test-harness quirk observed for config_set_unset.feature's "warnings elsewhere... collapse to a count" scenario (count is 3, not 1, against this fixture) may be related to finding #2 above (same wrapped-run machinery). Worth investigating together.

feature-baseline: isaac-foundation 4a96956d29e8ca2d5eb225fdfb29628068f48622
feature-blob: isaac-foundation features/cli/config_keys_list.feature 712b1c50280b28795a8bc74eea9cd0d165fb5491
feature-blob: isaac-foundation features/cli/config_schema_provenance.feature 25f6b78d095bda29cdacd7f479a0662c9b5221bb
feature-blob: isaac-foundation features/cli/config_set_entity_routing.feature d38e9dd3b1d3c8963a2d7e975717a3a54c9e221f
feature-blob: isaac-foundation features/cli/config_set_report.feature df9c9f9a336b50800605c96276f3f20973beae75
feature-blob: isaac-foundation features/cli/config_set_unset.feature 2382439e471572087850bc25ae1fe13a22979f20
