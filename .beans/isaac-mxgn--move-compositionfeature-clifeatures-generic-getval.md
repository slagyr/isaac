---
# isaac-mxgn
title: Move composition.feature + cli.feature's generic get/validate/sources scenarios to isaac-foundation
status: todo
type: task
priority: normal
created_at: 2026-09-30T02:43:45Z
updated_at: 2026-09-30T02:43:45Z
---

## Ruling

Micah, 2026-09-29: isaac-agent's `features/config/` holds ~100 scenarios
for foundation-owned config-command behavior that should move to
foundation with Marigold fixtures; agent keeps only the scenarios that
test agent's own schema (its registered-in?/exists? validations, its
required-field manifest declarations, its :cycle/:role/session-policy
concepts).

## Problem

`isaac-agent/features/config/composition.feature` (21 scenarios) and large
parts of `isaac-agent/features/config/cli.feature` (69 scenarios total —
this bean covers the get/validate/schema-drill/sources portion; set/unset/
keys/list is cleanup-bean-8) test isaac-foundation's own entity-file
composition, `${VAR}` substitution, and `config get`/`config validate`
mechanics — using crew/model/provider only as the example entity, not
because the behavior is agent-specific.

**Foundation already has some overlapping coverage** that must be checked
for redundancy before moving anything (see each row below):
`features/cli/config_resolution.feature` (redaction),
`features/cli/config_schema.feature` (isaac-3y69's fixture-based schema
rendering — this one looks like it may ALREADY fully cover cli.feature's
schema-drilling scenarios; see Group E below), `features/cli/
config_file_layout.feature` (key-as-file-vs-directory, not entity
composition per se), `features/cli/config_set_dotenv.feature` (staged-write
`.env` resolution, narrower than composition.feature's `${VAR}`
scenario).

## Mapping — composition.feature (21 scenarios)

MOVE (generic entity-dir/composition mechanics, Marigold-ize with a
fixture entity-dir; field named `:soul` becomes a generic companion field
name like `:notes` unless the scenario is specifically about companion
semantics, in which case keep a companion but rename it):

- "crew members are keyed by id"
- "loads a crew member from crew/<id>.edn"
- "loads a crew member from crew/<id>.md frontmatter"
- "soul loads from a companion .md file when :soul is absent"
- "defining soul in both :soul and <id>.md is an error"
- "derives crew id from filename when :id is not specified"
- "explicit :id must match filename"
- "unknown keys in entity files produce warnings but still load"
- "composes crew from isaac.edn and crew/*.edn additively"
- "composes models from isaac.edn and models/*.edn additively" — fold into
  the same generic scenario as the crew one above (one Marigold entity-dir
  proves the mechanism; a second one for "models" adds no new coverage)
- "composes providers from isaac.edn and providers/*.edn additively" — same,
  fold
- "duplicate crew id across isaac.edn and crew/*.edn is a hard error"
- "duplicate model id across isaac.edn and models/*.edn is a hard error" —
  fold, same reasoning
- "malformed EDN in a config file is reported with the file path"
- "${VAR} references are substituted from the environment" — CHECK against
  cleanup-bean-6's new `config_env_file.feature` first; likely redundant
  once that lands (delete rather than move if so)

STAYS (agent's own cross-entity referential validations —
`:model-exists?`/`:provider-exists?`/`:crew-exists?`-style checks wired to
agent's OWN manifest fields):

- "defaults.frequencies.crew must reference an existing crew"
- "defaults.crew.model must reference an existing model"
- "crew.model must reference an existing model"
- "model.provider must reference an existing provider"
- "crew references a model defined in models/<id>.edn"

CHECK (read full scenario body before deciding — not read in depth this
planning session):

- "no config files yields the built-in default config" — if this asserts
  foundation's own bare-config default, MOVE; if it asserts agent's
  manifest-declared defaults (e.g. a default crew/model), STAYS.

## Mapping — cli.feature (this bean's portion: get/validate/sources/schema-drill)

MOVE (generic `config get`/`config validate` mechanics; foundation's
`config_resolution.feature` already covers plain-get redaction generically
— check for exact overlap before duplicating, otherwise these fill real
gaps, especially `--reveal` which nothing in foundation covers yet):

- "config get redacts resolved ${VAR} values by default" — CHECK overlap
  with `config_resolution.feature`'s "launcher-backed config get still
  redacts ${VAR} values"; likely redundant, prefer delete over duplicate
- "config get --raw prints pre-substitution values"
- "config get --reveal shows real values after typed confirmation" — gap,
  nothing in foundation covers `--reveal` yet (cleanup-bean-6 added a
  `--reveal` USE, but not a scenario asserting the confirmation-refusal
  behavior itself)
- "config get --reveal refuses without typed confirmation" — same gap
- "config sources lists contributing files"
- "validate passes for a well-formed config"
- "validate reports errors with exit code 1"
- "validate reports warnings but still exits 0"
- "validate reads stdin as the full config and ignores on-disk files"
- "validate --as overlays stdin at the given config path before validating"
- "validate --as rejects file-path style with a hint to use a config path"
- "get prints a scalar value by dotted keyword path"
- "get prints a scalar value by bracket keyword path"
- "get prints a nested structure as EDN"
- "get exits non-zero for a missing key"
- "get redacts resolved ${VAR} values by default" — duplicate of the
  `config get` version above within the same file; fold to one
- "get --reveal shows the real value after typed confirmation" — fold
  with the `config get --reveal` version above
- "get --reveal refuses on invalid confirmation" — fold

STAYS (agent's own registered-in?/exists? validations, exercised through
agent's real manifest-declared entities — these are integration tests of
agent's OWN schema wiring, not of foundation's generic validate mechanism):

- "validate reports unknown llm api refs with file and valid set"
- "validate requires defaults.frequencies.crew" — **isaac-dnib pins this
  one explicitly as a regression check for its `demands-a-field?` cutover
  — do not delete or rewrite it out from under dnib; confirm dnib has
  landed and this scenario is still present/green in isaac-agent before
  touching this file at all**
- "validate reports unknown tool refs with file and valid set"
- "validate reports unknown provider refs with file and valid set"
- "validate reports unknown comm type refs with file and valid set"
- "validate warns when a crew directory includes the Isaac state root"
- "validate rejects the retired :role directory token"
- "validate accepts a crew on a session policy contributed by an installed module"
- "validate still rejects an unknown session policy and names the module's known set"

LIKELY ALREADY COVERED — DELETE AS REDUNDANT, DO NOT MOVE (Group E, schema
rendering: foundation's OWN `features/cli/config_schema.feature`, landed
under isaac-3y69, appears to be a near 1:1 generic replacement already —
worker must diff scenario-by-scenario to confirm before deleting, but the
titles line up almost exactly):

- "config schema prints the root schema with title, fields, and guidance"
  ↔ foundation's "root schema with no modules shows foundation's own
  fields and generated Try: examples" / "root schema lists a fixture
  module's dynamic-key config table"
- "config schema --tree expands every named sub-schema" ↔ foundation's
  "config schema --tree expands the fixture table's named sub-schema"
- "config schema crew renders the map wrapper with key/value rows" ↔
  foundation's "config schema <table> renders the map wrapper with
  key/value rows"
- "config schema crew.value prints the crew entity fields" ↔ foundation's
  "config schema <table>.value renders the entry fields"
- "config schema providers.key resolves the map-key spec" ↔ foundation's
  "config schema <table>.key resolves the map-key spec"
- "config schema providers.value prints the provider entity template" ↔
  same as crew.value, already covered
- "config schema crew.value.id prints the :id field schema" ↔ foundation's
  "config schema drills into a single field and shows its description"
- "config schema drills into a single field" ↔ same
- "config schema gives a friendly error for an invalid path" ↔
  foundation's "config schema gives a friendly error for a 2-segment
  typo, not a slot-id rewrite"
- "config help lists the schema subcommand" ↔ foundation's "help config
  lists the schema subcommand"
- "config schema --help describes the --tree flag" ↔ foundation's "config
  schema --help describes --tree with generic examples and no other
  module's names"

If the diff confirms equivalent coverage, delete these from
isaac-agent's cli.feature outright — no foundation-side addition needed,
they'd be pure duplication.

## Acceptance

- Foundation gains one new feature file (e.g.
  `features/cli/config_composition.feature`) covering the MOVE list above
  via Marigold fixtures, plus `--reveal`/`--reveal refuses` scenarios
  folded into an existing or new foundation get-behavior feature.
- isaac-agent's `composition.feature` shrinks to only the STAYS/CHECK-
  resolved-as-STAYS scenarios (cross-entity referential checks); its
  `cli.feature` loses the MOVE and redundant-Group-E scenarios, keeping
  only the STAYS list.
- Full suites green on both repos.
- isaac-dnib's pinned "validate requires defaults.frequencies.crew"
  scenario is untouched and still green.

## Likely repo scope

`isaac-foundation` (new feature file(s)), `isaac-agent` (trim
composition.feature and cli.feature to their STAYS lists).

## Notes

- This is the largest of the scenario-move beans by scenario count; split
  further into 2 sub-beans (composition.feature alone, then cli.feature's
  portion) if a single worker session runs long — no code dependency
  between the two files.
- Not blocked by isaac-dnib except for the one pinned scenario noted
  above (don't touch it, don't need to wait for dnib to land first since
  this bean doesn't touch dnib's files at all — normalize.clj/loader.clj/
  schema_base.clj/validation.clj aren't in scope here).

## Open questions

- Confirm the Group E "likely redundant" call with an actual side-by-side
  scenario diff before deleting — this planning session compared titles
  and feature-file structure, not full step-by-step bodies.

## Final mapping (planner, 2026-09-30)

Every agent scenario's disposition. The worker deletes the MOVED and DELETED-as-redundant originals from isaac-agent and keeps the STAYS ones. The 5 companion-.md scenarios stay in agent until the companion-loading bean lands. `config_set_comm_field.feature` (4 scenarios) was dropped from this bean: it behaves differently under the test harness than the CLI, so its originals stay in agent.

# isaac-mxgn mapping — final disposition

Drafts written (not committed) to a foundation worktree:
`/Users/micahmartin/agents/isaac/plan/isaac-foundation-mxgn-draft/features/cli/`
- `config_composition.feature` — 10 scenarios, all @wip, all dry-run GREEN.
- `config_get_validate.feature` — 15 scenarios, all @wip, all dry-run GREEN.

Fixture ids used: `marigold.mxgn.vessels` (composition), `marigold.mxgn.charts`
(get/validate). Both manifest-only (no :factory/deps.edn/src).

## composition.feature (isaac-agent) — 21 original scenarios

| Agent scenario | Disposition |
|---|---|
| crew members are keyed by id | moved-to: config_composition.feature:"vessel members are keyed by id" |
| loads a crew member from crew/<id>.edn | moved-to: config_composition.feature:"loads a vessel from vessels/<id>.edn" |
| loads a crew member from crew/<id>.md frontmatter | moved-to: config_composition.feature:"loads a vessel from vessels/<id>.md frontmatter" |
| soul loads from a companion .md file when :soul is absent | **stays-in-agent: blocked**. Confirmed empirically: the config-LOAD side of the companion mechanic (`isaac.config.companions/companion-md-relative`) is hard-coded to kinds `:crew` (→ `:soul`) and `:berths` (→ `:ledger`) only. Declaring `:companion {:field :soul-or-ledger}` on any other kind crashes every entity load in that table (nil path segment concatenates into the config dir itself → `FileNotFoundException ... (Is a directory)`). The WRITE side (`isaac.config.mutate/companion-spec`) already reads a module's own `:companion` descriptor generically — this is a real, narrow foundation gap. Recommend a small follow-up bean to generalize `companion-md-relative` before re-attempting this move. |
| defining soul in both :soul and <id>.md is an error | **stays-in-agent: blocked**, same reason as above. |
| derives crew id from filename when :id is not specified | moved-to: config_composition.feature:"derives vessel id from filename when :id is not specified" |
| explicit :id must match filename | moved-to: config_composition.feature:"explicit :id must match filename" (fixture needed an explicit `:id` schema field — without one, `:id` is silently treated as an unrecognized key rather than validated against the filename) |
| unknown keys in entity files produce warnings but still load | moved-to: config_composition.feature:"unknown keys in entity files produce warnings but still load" |
| composes crew from isaac.edn and crew/*.edn additively | moved-to: config_composition.feature:"composes vessels from isaac.edn and vessels/*.edn additively" |
| composes models from isaac.edn and models/*.edn additively | folded into the scenario above (same mechanism, one fixture table) |
| composes providers from isaac.edn and providers/*.edn additively | folded into the scenario above |
| duplicate crew id across isaac.edn and crew/*.edn is a hard error | moved-to: config_composition.feature:"duplicate vessel id across isaac.edn and vessels/*.edn is a hard error" (fixture needed `:merge-root-entity? true` on the entity-dir descriptor — without it, an isaac.edn-inline entry and a file-based entry don't cross-check for duplicates at all, they just don't merge) |
| duplicate model id across isaac.edn and models/*.edn is a hard error | folded into the scenario above |
| malformed EDN in a config file is reported with the file path | moved-to: config_composition.feature:"malformed EDN in a config file is reported with the file path" |
| ${VAR} references are substituted from the environment | moved-to: config_composition.feature:"${VAR} references are substituted from the environment". Judgment call: NOT deleted as redundant against config_env_file.feature (isaac-yo8d) — that file always exercises substitution together with `.env`-file / `--reveal` CLI semantics; this scenario proves the substitution mechanism at plain config-load time with no `.env` file involved, a materially different seam. Noted as a judgment call, not asserted with full certainty. |
| defaults.frequencies.crew must reference an existing crew | stays-in-agent (as mapped): agent's own cross-entity referential validator |
| defaults.crew.model must reference an existing model | stays-in-agent (as mapped) |
| crew.model must reference an existing model | stays-in-agent (as mapped) |
| model.provider must reference an existing provider | stays-in-agent (as mapped) |
| crew references a model defined in models/<id>.edn | stays-in-agent (as mapped) |
| no config files yields the built-in default config | stays-in-agent: resolved CHECK — asserts agent's own manifest-declared defaults (`defaults.crew.model: llama`, `models.llama...`), not foundation's bare-config default. Not moved. |

## cli.feature (isaac-agent) — this bean's portion (get/validate/sources/schema-drill)

| Agent scenario | Disposition |
|---|---|
| config is registered and has help | not in original MOVE list — left as-is (static help, not itemized in the bean's mapping) |
| config validate has its own help page via --help | not in original MOVE list |
| config help validate is an alternate way to reach subcommand help | not in original MOVE list |
| config get redacts resolved ${VAR} values by default | moved-to: config_get_validate.feature:"config get redacts resolved ${VAR} values by default; an unresolved ${VAR} is named in :unresolved-refs" — **behavior changed since isaac-dnib**: the whole-config dump no longer shows an inline `<VAR:UNRESOLVED>` marker at the field's own path; it drops the key (same as absent) and names the miss once in a top-level `:unresolved-refs` map keyed by dotted path. Scenario rewritten to match current behavior; flagging this in case the marker's disappearance was an unintended dnib side effect worth a look. |
| config get --raw prints pre-substitution values | moved-to: config_get_validate.feature (same title) |
| config get --reveal shows real values after typed confirmation | moved-to: config_get_validate.feature (same title) |
| config get --reveal refuses without typed confirmation | moved-to: config_get_validate.feature (same title) |
| config sources lists contributing files | moved-to: config_get_validate.feature (same title) |
| validate passes for a well-formed config | moved-to: config_get_validate.feature (same title) |
| validate reports errors with exit code 1 | moved-to: config_get_validate.feature (same title); trigger Marigold-ized to a missing `:required true` field rather than agent's `defaults.frequencies.crew` |
| validate reports unknown llm api refs with file and valid set | stays-in-agent (as mapped) |
| validate requires defaults.frequencies.crew | stays-in-agent — **isaac-dnib pinned regression, untouched** |
| validate reports unknown tool refs with file and valid set | stays-in-agent (as mapped) |
| validate reports unknown provider refs with file and valid set | stays-in-agent (as mapped) |
| validate reports unknown comm type refs with file and valid set | stays-in-agent (as mapped) |
| validate reports warnings but still exits 0 | moved-to: config_get_validate.feature (same title) |
| validate warns when a crew directory includes the Isaac state root | stays-in-agent (as mapped) |
| validate rejects the retired :role directory token | stays-in-agent (as mapped) |
| validate reads stdin as the full config and ignores on-disk files | moved-to: config_get_validate.feature (same title) |
| validate --as overlays stdin at the given config path before validating | moved-to: config_get_validate.feature (same title) |
| validate --as rejects file-path style with a hint to use a config path | moved-to: config_get_validate.feature (same title) |
| get prints a scalar value by dotted keyword path | moved-to: config_get_validate.feature (same title) |
| get prints a scalar value by bracket keyword path | moved-to: config_get_validate.feature (same title) |
| get prints a nested structure as EDN | moved-to: config_get_validate.feature (same title) |
| get exits non-zero for a missing key | moved-to: config_get_validate.feature (same title) |
| get redacts resolved ${VAR} values by default | folded into "config get redacts..." above |
| get --reveal shows the real value after typed confirmation | folded into "config get --reveal shows..." above |
| get --reveal refuses on invalid confirmation | folded into "config get --reveal refuses..." above |
| validate accepts a crew on a session policy contributed by an installed module | stays-in-agent (as mapped) |
| validate still rejects an unknown session policy and names the module's known set | stays-in-agent (as mapped) |

### Group E — schema-drilling, deleted as redundant (not moved)

Diffed scenario bodies (not just titles) against `config_schema.feature` (isaac-3y69). All ten confirmed as the same generic mechanism, exercised through a different table name:
- config schema prints the root schema with title, fields, and guidance
- config schema --tree expands every named sub-schema
- config schema crew renders the map wrapper with key/value rows
- config schema crew.value prints the crew entity fields
- config schema providers.key resolves the map-key spec
- config schema providers.value prints the provider entity template
- config schema crew.value.id prints the :id field schema
- config schema drills into a single field
- config schema gives a friendly error for an invalid path
- config help lists the schema subcommand
- config schema --help describes the --tree flag

All ten: **deleted as redundant: covered by config_schema.feature** (see file header for the exact scenario-to-scenario correspondence already documented there by isaac-3y69/isaac-mxgn planning).

## Open questions / follow-ups

1. **Real production gap**: `isaac.config.companions/companion-md-relative` (config-LOAD side of the soul/ledger companion mechanic) is hard-coded to `:crew`/`:berths`, unlike the generic write-side `isaac.config.mutate/companion-spec`. Blocks moving 2 composition.feature scenarios and 3 more from the 601n side (see 601n-mapping.md). Recommend a small foundation bean to generalize it (read the descriptor's own `:companion` field, same as the write side already does).
2. The `${VAR}` substitution scenario's redundancy call against config_env_file.feature is a judgment call, not a certainty — flagging for a second look.
3. The dnib-driven change to whole-config `config get`'s unresolved-${VAR} rendering (inline marker → separate `:unresolved-refs` map) may or may not be intentional; noted in the feature file's scenario comment, not asserted as a bug.

feature-baseline: isaac-foundation 4a96956d29e8ca2d5eb225fdfb29628068f48622
feature-blob: isaac-foundation features/cli/config_composition.feature 9c47a7f9327eea17a9ccb5c9eb2ead2a4ced91e9
feature-blob: isaac-foundation features/cli/config_get_validate.feature 60161ffc7c91fa32f81875b11a8f9b01f6e7736a
