---
# isaac-lshz
title: 'isaac-handbook: handbook__configure (atomic multi-set, prose fields)'
status: in-progress
type: feature
priority: normal
created_at: 2026-09-30T00:28:23Z
updated_at: 2026-09-30T05:09:26Z
blocked_by:
    - isaac-z90t
    - isaac-cvri
    - isaac-c4em
---

Design notes: Micah + planner, 2026-09-29. Not scenario-ready; draft to hold
decisions.

## Decided (Micah, 2026-09-30, round 3)

- **New-entity placement follows `config set`'s existing precedent — no
  per-call override.** Order: (1) editing an existing entry writes it where
  it already lives; (2) adding an entry to a kind whose other entries are
  ALL entity files → the new one becomes a file too; (3) else, if
  `prefer-entity-files` is set → new entity file; (4) else → inline in root
  `isaac.edn`. Confirmed rule 2 is the ONE piece missing from
  `isaac.config.mutate` today (see isaac-lshz-prereq) — fixing it there
  benefits plain `isaac config set` too, not just `handbook__configure`.
  `handbook__configure` calls the same write path and gets the fix for
  free; no tool-specific flag.
- **Secrets: accepted as drafted.** Refuse a literal write over a path
  whose current value is a `${VAR}` reference; no `.env` access; the
  "can't catch a brand-new literal secret" gap is known and accepted for
  v1. A schema-level `:secret true` field marker is a possible follow-up
  (would let `config get`/`handbook__read`'s reference topics redact by
  declaration instead of by scanning for `${VAR}` usage) — not this bean.
- **Response: plain prose**, like `handbook__read` — what was written,
  where, plus any warnings. No structured `--edn`/`--json` output.

## What it is

`handbook__configure`, the isaac-handbook module's second tool, granted separately from `handbook__read`. It lets a crew change Isaac's config without shell or root-file access (goal: take Yopp off `exec/run` and root writes).

## Decided

- Same semantics as `isaac config set` / `unset`, through foundation's single config-write path (no second writer): validate, write, hot-reload. Never writes defaults.
- **Several path/value pairs per call, applied atomically** (like `config set`'s stdin-map form). Fields valid only together land together; an invalid combination is refused whole, nothing written. No `--force`.
- Must handle **companion prose fields** (e.g. a cron job's `:prompt`, a crew's soul), not just EDN values: cron jobs are config (`cron/<name>.md` with frontmatter).
- Secrets: never write secret values inline; reference `${VAR}` (open: can a crew write `.env`? probably not).
- The response confirms what was written and where, plus validation warnings, like `config set`'s report.

## Decided (Micah, 2026-09-29, round 2)

- **No `.env` access.** A crew never reads or writes `.env` or secret values; it references `${VAR}` and a human sets the value.
- **Creating new entries is in scope for v1** (a new crew, a new cron job, a new comm): an entity file (EDN or markdown with frontmatter + companion prose) is created through configure. Deleting entries: include if cheap, same path as unset of the entry.
- **Every configure call is logged**: calling crew, session, the pairs requested, and the outcome (written / refused + reason).

## Wanted

`handbook__configure(set?: {<dotted.path>: <value>, ...}, unset?: [<dotted.path>, ...])`
— at least one of `set`/`unset` non-empty.

- **One atomic batch per call.** Every `set` and `unset` pair is parsed and
  planned, then staged and validated as ONE resulting config (not path by
  path). Any blocking error on any pair refuses the ENTIRE call — nothing is
  written, including pairs that were individually valid. This requires a new
  foundation primitive (see isaac-lshz-prereq below); `isaac.config.mutate`
  today only applies one dotted path per call.
- **Companion prose rides in the same call**, using foundation's existing
  companion-field machinery unchanged (`isaac.config.mutate`'s
  `companion-spec`/`use-companion-markdown?`/frontmatter split, driven by
  each module's manifest `:companion {:field ... :mode ...}` — already how
  cron's `:prompt` and crew's `:soul` work today for `isaac config set`).
- **Creating a new entity** (new crew, new cron job, new comm) via a
  whole-entity `set` (e.g. `{"set":{"cron.hull-watch":{...}}}`) follows
  `config set`'s own existing placement precedent (isaac-lshz-prereq #1):
  it lands as its own `<entity-dir>/<id>.edn` (+ companion `.md` when the
  companion field is long/required) when its siblings are already all
  entity files (the normal case for crew/cron once a handbook-managed
  instance has any of them), falls back to `prefer-entity-files`, then to
  inline root `isaac.edn` — same rule the CLI follows, no tool-specific
  override.
- **Deleting an entity** = `unset` of the whole entity path (e.g.
  `{"unset":["crew.marvin"]}`) — same code path as unset of any field,
  removes the entity file (and its companion `.md`/frontmatter) entirely.
- **Secrets / `.env`:**
  - `handbook__configure` never reads `.env` and never resolves `${VAR}`
    references to their live value — it only ever writes/removes the
    literal config-file text (same as `isaac config set`/`unset`; neither
    reads `.env` for its own writes today).
  - A `set` whose target path's *current* value is a `${VAR}` reference is
    refused unless the new value is *also* a `${VAR}` reference — a crew can
    repoint a secret at a different env var name, never overwrite it with a
    literal. This is new validation in the tool handler (not a foundation
    schema concept — there is no `:secret` field type; foundation only knows
    `${VAR}` interpolation and CLI-side redaction by matching literal env
    values in printed output, see `isaac.config.cli.common/redact-threaded-config`).
  - No config path ever addresses `.env` itself (dotted paths only reach
    `isaac.edn` / entity files), so there is no separate "path is `.env`"
    case to refuse — the refusal above is the whole enforcement surface.
- **Response** confirms, per pair, the dotted path, the value (or "unset"),
  and the relative file it landed in/was removed from — extending
  `mutate-common/print-confirmation!`'s one-pair phrasing to a list. On
  refusal: which pair(s) failed and why, and that nothing was written.
- **Logging**: `:handbook/configure` (info on success, warn/error on
  refusal) with `:crew`, `:session`, `:pairs` (dotted paths + values, any
  path currently holding — or being set to — a `${VAR}` reference logged as
  the reference text, never a resolved secret), and `:outcome`
  (`:written`/`:refused` + reason). Follows the `:config/set`/`:config/unset`
  convention in `isaac.config.cli.mutate-common/log-mutation!`, adapted for
  a batch.
- **Grant**: `crew :tools :allow [:handbook/configure]`, independent of
  `handbook/read` (mirrors `isaac-manifest.edn`'s
  `:isaac.agent/tools {:handbook__read {...}}` pattern, adding
  `:handbook__configure {:factory isaac.handbook.tools/configure-tool-factory}`).
- **Handbook chapter**: isaac-handbook's own `:handbook` chapter documents
  `handbook__configure` (what it does, the atomic-batch contract, the
  secret-write refusal) per the "keep the handbook current" rule — it's the
  first module whose OWN chapter needs updating by this bean, not a
  downstream module's.

## Acceptance

- `isaac-handbook/features/configure.feature` (drafted, all `@wip` pending
  baseline) — 12 scenarios: single set; atomic multi-set of two fields valid
  only together; an invalid pair refuses the whole call with nothing
  written; unset; create a new cron job (follows the sibling-file
  precedent, prose splits to a companion); create a new crew (same
  precedent, companion soul); refusal when the crew isn't granted
  `handbook__configure`; the response names every file a multi-file batch
  touched; the `:handbook/configure` log entry; a literal write over a
  `${VAR}`-referenced secret is refused; an unrecognized config path refuses
  the whole call; unsetting a whole entity removes its file.
- Dry run (all scenarios untagged, run against current `main`): 11 of 12
  fail on `"unknown tool: handbook__configure"` (or, for the log scenario,
  a real `:drive/turn-accepted` entry where `:handbook/configure` is
  expected) — confirming Background/fixtures/grants all work today
  (including the new sibling-file fixtures for crew and cron that
  scenarios 5/6 rely on) and the only missing piece is the tool itself. The
  12th ("unavailable unless granted") passes trivially today since the
  tool doesn't exist yet; it will keep passing once implemented.
- **Foundation prerequisite** (isaac-lshz-prereq, separate bean, currently
  `draft`, blocks this one): (a) the missing "siblings all files → new
  file" placement rule in `isaac.config.mutate`'s `choose-set-location` —
  a plain `config set` bug fix, proven at the CLI level in
  `features/cli/config_set_new_entity_placement.feature` (4 scenarios,
  `@wip`; dry run against real `main`: exactly 1 fails — the "new entry,
  siblings all files" case — the other 3 already pass, unaffected); (b)
  the atomic multi-path write primitive itself, drafted in
  `features/config/atomic_multiset.feature` (5 scenarios, `@wip`, not
  dry-run — no step definitions exist yet for the new primitive).
- Handbook chapter update for isaac-handbook itself (see Wanted).
- `bb spec` / `bb features` green in isaac-handbook and isaac-foundation.

## Likely repo scope

- **isaac-foundation** (prerequisite, separate bean isaac-lshz-prereq):
  `src/isaac/config/mutate.clj` (the `choose-set-location` sibling-files
  fix; new `set-many!`/equivalent); `features/cli/config_set_new_entity_placement.feature`
  (new); `spec/isaac/config/mutate_spec.clj` (unit coverage for both — the
  batch primitive is a pure function with no CLI surface, so specs carry
  more of its proof than a `.feature` file does).
- **isaac-handbook** (this bean): `resources/isaac-manifest.edn` (register
  `:handbook__configure`); `src/isaac/handbook/tools.clj` (new
  `configure-tool-factory`/`configure` handler, or a new
  `src/isaac/handbook/configure.clj` if `tools.clj` gets crowded — mirrors
  `read-tool-factory`/`read-topic`); handbook chapter markdown — isaac-handbook's own manifest carries no
  `:handbook` key yet (checked: `resources/isaac-manifest.edn` has no
  `:handbook` classpath-resource entry), so this bean adds isaac-handbook's
  *first* self-describing chapter, not an edit to an existing one;
  `features/configure.feature`;
  `spec/isaac/handbook/configure_spec.clj`.
- No isaac-agent or isaac-server changes expected — the tool is a pure
  isaac-handbook + isaac-foundation seam, same as `handbook__read`.

## Notes

- Confirmed by reading `isaac.config.mutate` in full: `set-config`/
  `unset-config` each take exactly one dotted path today. The CLI's
  "stdin-map form" (`echo '{...}' | isaac config set models.echo -`) already
  gives atomicity for **several fields sharing one parent path** (one call,
  one entity) — that alone covers "two fields on the same entity, valid only
  together." It does **not** cover pairs across **different top-level
  paths/entities** in one refuse-or-commit-together call, which
  `handbook__configure`'s wire contract needs (a crew can ask to grant a
  tool AND create a companion cron job in one call). Confirmed no existing
  `set-many`/batch-mutation helper anywhere in isaac-foundation.
- Companion-field writing (item 2 of the investigation) already has a full
  write path in `isaac.config.mutate`: `companion-spec` (reads the owning
  module's `:companion {:field ... :mode ...}` descriptor — e.g. cron's
  `:prompt`/`:required`, crew's `:soul`/`:exclusive`), `use-companion-markdown?`
  (splits a long inline value — over `companion-inline-limit` = 64 chars —
  into its own `.md` file), and `update-frontmatter` (YAML frontmatter
  read/write preserving unrelated keys and byte-for-byte body). No new
  foundation work needed here — `handbook__configure` calls the same path
  `isaac config set` already exercises for existing entities.
- New-entity creation (item 3): Micah's ruling is that placement follows
  `config set`'s own precedent — edit-where-it-lives, then
  siblings-all-files, then `prefer-entity-files`, then inline — with no
  tool-specific override. Reading `choose-set-location` line by line
  confirmed rules 1, 3, and 4 already exist; rule 2
  ("siblings-all-files → new file") is the one gap, and it's a `config set`
  bug, not a `handbook__configure` concern — fixed once in
  isaac-lshz-prereq, both callers benefit. (Superseded an earlier draft of
  this bean that proposed a per-call `:prefer-entity-files?` override —
  dropped per Micah's 2026-09-30 ruling.)
- Secrets (item 4): there is no `:secret` schema field type anywhere in
  foundation. A "secret" is just a value written as `${VAR}`; redaction is
  purely presentational, at `config get`/CLI-print time
  (`isaac.config.cli.common/redact-threaded-config`, string-replacing any
  live env-var value it finds echoed back from a `${TOKEN}` reference found
  in a config source file). `handbook__configure` therefore can't rely on a
  schema-level "this field is a secret" marker — the refuse-literal-over-
  `${VAR}` rule above is a config-mutate-layer policy addition, not a
  foundation schema change.
- Log event (item 5): `:handbook/configure`, following the
  `isaac.config.cli.mutate-common/log-mutation!` shape
  (`log/log* level event file line :key val ...`), adapted so `:pairs`
  carries the whole batch rather than one `:path`/`:value` pair.
- All 12 `configure.feature` scenarios' step vocabulary already exists
  (verified with `gherclj match` from isaac-handbook's `:features:dev-local`
  alias) — no new gherclj steps needed for this bean's own feature file:
  `the following model responses are queued:`, `isaac is run with`,
  `session "..." has transcript matching:`, `the crew "..." allows tools:`,
  `the isaac EDN file "..." exists with:`, `the isaac file "..." EDN
  contains:`, `the isaac file "..." does not exist`, `the isaac file "..."
  exists`, `the config file "..." does not contain "..."`, `the isaac
  config path "..." is "..."`, `the env var "..." is set to "..."`, `the
  log has entries matching:`, `the prompt does not have tools:`.

## Open questions

Resolved 2026-09-30 (Micah): placement follows `config set`'s precedent
with no override (see Decided, round 3); secrets accepted as drafted, with
a `:secret true` schema marker noted as a possible follow-up, not this
bean; response is plain prose. Remaining, non-blocking:

1. **`set-many!` naming and result shape** — left to whoever implements
   isaac-lshz-prereq; not a blocking design question.
2. **Secret-refusal rule scope** (accepted, noted for the record): the
   "refuse literal over an existing `${VAR}` reference" rule only catches
   *overwriting* an already-referenced field. It does not catch a crew
   setting a **brand new** field to what happens to be a real
   secret-looking literal — there's no way to know that without reading
   `.env`, which is forbidden. The defense is the handbook chapter
   instructing crews to always use `${VAR}`, not a hard technical
   guarantee for first-time secret fields.

feature-baseline: isaac-handbook 48d5e794bfa917b0bfeb9e8e4736a3e1889c9f50
feature-blob: isaac-handbook features/configure.feature de9bc2198c4bdfab38fc9c8b8cef9e2c3c810c3e

feature-baseline: isaac-handbook 09cc43bcadc019fe25dc5e94178f91dc6a6af2a1
feature-blob: isaac-handbook features/configure.feature 05cc0b80d44958a94b268fc2c39f1d6b8f774a57

## Conflict — 2 of 12 scenarios cannot pass as written (2026-09-30)

Implemented `handbook__configure` on `bean/isaac-lshz` (isaac-handbook,
commit e65718c, pushed): atomic multi-set/unset through foundation's
`isaac.config.mutate/set-many!`, whole-entity + companion-field writes,
`${VAR}`-secret-write refusal, `:handbook/configure` logging, manifest
grant, isaac-handbook's own `:handbook` chapter, README tools section.
Bumped isaac-handbook's foundation pin to `fd91dd101ff41ed5ea85d1b245536a9b0bb7afb9`
(current main: set-many! + isaac-c4em placement fix + isaac-h2oo's
validator-ref relocation, which landed live mid-session) and its isaac-agent
pin to `7d3910f2c4d02bcbaf6d8bc87f166c0ceb59b58a` (isaac-h2oo's agent-side
lexicon contribution — required, since the old agent pin predates the
validator-ref move and no longer boots against new foundation). Added
isaac-cron as a `:features`-only test dependency (pinned
`9c13a6a43c911f3d4595b31bca295615c75bbb9d`) — the cron-entity scenarios need
real `:cron` schema on the classpath (isaac-cron is `:builtin? true`,
auto-discovered via `isaac.module.discovery/classpath-builtin-index`; without
it "cron.hull-watch" is refused as an unrecognized root key, correctly).

8 of 12 scenarios are green with these changes. Real (non-dev-local) `bb ci`
against the bumped pins is green. `configure.feature` itself is untouched
(`@wip` intact) — I did not remove it, since removing it selectively would
misrepresent the contract. Two scenarios are conflicts, not implementation
gaps:

**1. "atomic multi-set writes two fields that are only valid together"**
(`models.riptide.model` / `models.riptide.provider`) asserts the new
`riptide` model lands inline in `isaac.edn`. But `models` is declared
`:entity-dir "models"` in isaac-agent's manifest, and the Background sets
`:prefer-entity-files true` — so per the landed isaac-c4em rule ("new entry:
its own entity file if `:prefer-entity-files` is true, otherwise inline"),
a brand-new `models.riptide` correctly becomes its own `models/riptide.edn`
file. Confirmed empirically (it does land there, `models/riptide.edn`
exists with the right content) and confirmed against foundation's own
already-passing precedent,
`isaac-foundation/features/cli/config_set_many.feature` scenario "a new
whole-entity value in a batch follows the same placement preference as
config set (isaac-c4em)" — same pattern, same rule, asserts the file
placement (not inline) for exactly this reason. The scenario's choice of
`models.*` for a "two fields, inline" example predates realizing `models` is
entity-dir; it needs a different root key (or an assertion against
`models/riptide.edn` instead of `isaac.edn`) to match the rule this same
bean's own prerequisite (isaac-cvri/isaac-c4em) already landed. Per
`hail-bean-work-gate`: "the scenarios themselves are wrong ... contradict
the code" → conflict for the planner, not a worker fix. No tool-specific
placement override is applicable per this bean's own Decided-round-3 ruling.

**2. Three scenarios** ("an invalid pair in the batch refuses the whole
call...", "unset removes a field through the same tool", "an unrecognized
config path refuses the whole call") each assert, after a refused or
partial call, that `config/crew/marvin.edn`'s `model` field is unchanged —
via `And the isaac file "config/crew/marvin.edn" EDN contains: | model |
grover |`. This can never pass, independent of `handbook__configure`:
isaac-foundation's own spec-support,
`isaac.foundation.fs-steps/parse-isaac-value`, special-cases `path ==
"model"` to a keyword only when its `file-path` argument contains
`"/config/crew/"` (leading slash). The Given-step fixture
(`isaac-edn-file-exists`) calls it with the ABSOLUTE path (has the leading
slash, from `<root>/config/crew/marvin.edn`) → writes `:grover` (keyword).
The Then-step assertion (`isaac-file-edn-contains`) calls it with the RAW
relative path exactly as typed in the feature (`"config/crew/marvin.edn"`,
no leading slash) → the same check fails → expects the literal string
`"grover"`. Reproduced in complete isolation, with no `handbook__configure`
code in the picture at all: a bare `Given the isaac EDN file
"config/crew/marvin.edn" exists with: model | grover` followed by `Then the
isaac file "config/crew/marvin.edn" EDN contains: model | grover` already
fails with `Expected: "grover" got: :grover`. This is a pre-existing
asymmetry in `isaac-foundation`'s shared `spec-support` (write path vs. read
path pass a differently-shaped `file-path` into the same special-case
check), unrelated to and unfixable from isaac-handbook. It would affect any
feature anywhere that both seeds a crew's `:model` this way AND later
asserts it unchanged with an exact `EDN contains` check — scenarios 1, 5,
and 6 avoid it only because they assert the field **we just wrote**
(a plain string, matching the read side's un-prefixed-path expectation).

Left `in-progress`, not `unverified` — no gate run yet (blocked pre-suite,
not post-green). Branch `bean/isaac-lshz` pushed to isaac-handbook with the
implementation, deps bumps, and a full spec file (`configure_spec.clj`, 8
examples). Worktree left at `isaac-handbook-lshz` for continuity. Options
for the planner: (a) re-baseline the multi-set scenario against
`models/riptide.edn` placement (or swap its fixture path to a non-entity-dir
key), and either re-baseline the three "unchanged model field" scenarios to
a substring check (`does not contain`) the way
`config_set_undeclared_key.feature` does, or fix the `parse-isaac-value`
asymmetry in `isaac-foundation`'s spec-support (small, mechanical, but a
separate repo/bean); (b) accept the current behavior and re-baseline those
three scenarios' literal expectations to `:grover` (keyword) to match the
fixture step's actual write, if that is judged acceptable.
