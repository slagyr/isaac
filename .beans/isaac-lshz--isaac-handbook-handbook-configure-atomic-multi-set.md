---
# isaac-lshz
title: 'isaac-handbook: handbook__configure (atomic multi-set, prose fields)'
status: todo
type: feature
priority: normal
created_at: 2026-09-30T00:28:23Z
updated_at: 2026-09-30T03:00:10Z
blocked_by:
    - isaac-z90t
    - isaac-cvri
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
