---
# isaac-3y69
title: 'config schema is generic: foundation never names another module''s config'
status: todo
type: feature
priority: high
created_at: 2026-09-29T19:24:33Z
updated_at: 2026-09-29T19:24:33Z
---

## Ruling

Micah, 2026-09-29: foundation never names another module's config, berths, or
ids. Everything a foundation CLI command needs comes from the loaded schema
and module manifests, not from a hard-coded field/berth name.

## Problem

`isaac config schema` (`isaac-foundation/src/isaac/config/cli/schema.clj`)
violates the ruling in four places:

- `schema.clj:17-23` — the `examples` string ("Try:" block) hard-codes agent
  paths: `crew`, `providers.value`, `crew.value.model`, `providers.value.api-key`.
- `schema.clj:25-34` — the `help` description explains the `key`/`value`
  path convention using `crew.value` / `crew.value.soul`.
- `schema.clj:48-62` — `collection-surfaces #{"comms" "providers"}` is the
  fixed set of table names allowed to use slot-id drilling
  (`comms.discord.token` -> `comms.value.token`, with a guard so a 2-segment
  typo like `providers.valued` still 404s instead of silently rewriting).
  Any other dynamic-key table (e.g. a fixture's `:relays`, or a future
  module's own table) can't be slot-id-drilled at all today.
- `schema.clj:42-46,91` — `:options-resolvers {:comms (comm-resolver
  module-index)}` wires the `options:` line for a `:type` field to
  `isaac.config.comm-kinds/comm-kinds`, which reads the agent's
  `:isaac.agent/comm` berth by literal id. No other berth's field can ever
  get an `options:` line through this path.

## Wanted

1. **Examples and help are generated from the loaded schema**, not written
   by hand. Concrete rule (same rule for both):
   - Start with the bare command: `isaac config schema`.
   - Add `isaac config schema <field>` for the first ROOT field, sorted by
     name, whose type is a plain leaf (not `:map`, `:seq`, or `:one-of`).
     Skip this line if no such field exists.
   - Add `isaac config schema <table>` and `isaac config schema
     <table>.value` for the first ROOT field, sorted by name, that has BOTH
     a `:key-spec` and a `:value-spec` (a dynamic-key map). Skip both lines
     if no such field exists.
   - Always end with `isaac config schema --tree`.
   - The live `config schema` root command computes this from the actual
     resolved/composed root (module-index included) — with foundation alone
     that's `hot-reload` (leaf) and `modules`/`modules.value` (dynamic-key).
   - The static `--help` text has no config/module context today (`help-text`
     fns are called with no `opts` — `isaac.config.cli.command/run-parsed-
     subcommand` never passes them through). Rather than plumb `opts` into
     every subcommand's `--help` path, `--help` always renders this rule
     against **foundation's own `schema-base/base-root`**, unconditionally —
     the same output as the foundation-only root scenario, regardless of
     which modules are actually installed. Flagged as a known trade-off
     below.
   - The description paragraph in `help` should stop citing `crew.value` /
     `crew.value.soul` and instead illustrate the `key`/`value` convention
     with foundation's own `modules.value` / `modules.key`.

2. **Slot-id drilling generalizes to any dynamic-key map.** Replace the
   `collection-surfaces #{"comms" "providers"}` check with: resolve the
   first segment via `schema-path/schema-at`; if that spec has both a
   `:key-spec` and a `:value-spec`, and the path has 3+ segments, and the
   second segment isn't already `key`/`value`, rewrite the second segment to
   `value`. Keeps the existing 2-segment typo guard (`providers.valued`,
   `relays.valued`, etc. still 404 instead of being silently rewritten)
   because it's a property of segment count, not of the table's name.

3. **`options:` comes from the field's own `[:registered-in? <berth-id>]` (or
   `[:registered-in? <berth-id> <config-path>]`) validation**, not a
   CLI-side `:options-from`/`:options-resolvers` map keyed by hand-picked
   table names. `isaac.schema.registered-in/registered-in?` already builds
   exactly this accepted-id list via its `:known` thunk (module-index +
   optional config-path union, already module-index/config generic). The
   term renderer (`isaac.config.schema.term`) should scan a field's
   `:validations` for a `[:registered-in? berth-id config-path?]` entry and
   call that validation's `:known` fn to get the `options:` values, with
   `isaac.config.cli.schema` binding `isaac.schema.registered-in/*module-index*`
   and `*config*` before rendering (mirrors how `isaac.module.berths` already
   binds them for contribution validation). This makes `:options-from` /
   `:options-resolvers` unnecessary for this case — grep shows it isn't set
   on any real production field today (only in unit-spec fixtures), so
   nothing currently in production depends on keeping it.
   `isaac.config.comm-kinds` (and the near-identical
   `isaac.http.module/comm-kinds` in isaac-server) become dead code once
   `isaac.config.cli.schema` stops calling them — delete both, clean
   cutover, no back-compat shim. (isaac-server's copy is out of this bean's
   repo scope but should get its own follow-up bean once this pattern is
   confirmed.)

   **`:configurable? false` hides an entry from `options:` only (Micah,
   2026-09-29).** A contribution whose manifest value is a map with
   `:configurable? false` is left out of the displayed `options:` list, for
   any berth. Validation is unchanged: `:registered-in?` still accepts those
   ids, so no config that validates today starts failing. Agent's
   `schema_cli_options.feature` ("lists user-configurable comm kinds") relies
   on this exclusion and must stay green.

## Acceptance

- `isaac-foundation/features/cli/config_schema.feature`. 12 scenarios, all currently `@wip`, all
  dry-run confirmed:
  - root schema, no modules: foundation's own fields + generated Try: lines — **fails correctly** today (still hard-coded crew/providers Try:).
  - root schema lists a fixture module's dynamic-key table — already passes.
  - `<table>` renders the map wrapper (key/value rows) — already passes.
  - `<table>.value` renders entry fields — already passes.
  - `<table>.key` resolves the key-spec — already passes.
  - drilling into a single field shows its description — already passes.
  - slot-id drilling on the fixture table — **fails correctly** today (404s; `relays` isn't in the hard-coded set).
  - 2-segment typo still 404s (not silently rewritten) — already passes.
  - `--tree` expands the fixture's named sub-schema — already passes.
  - a `[:registered-in?]` field lists the berth's entries as `options:`, excluding the `:configurable? false` one — **fails correctly** today (no `options:` line at all; nothing wires a resolver for this berth).
  - `help config` lists the schema subcommand — already passes.
  - `config schema --help` describes `--tree` with generic examples, no agent names — **fails correctly** today (description/examples still say crew/providers).

- Remove `@wip` from all 12 scenarios once `isaac.config.cli.schema` /
  `isaac.config.schema.term` / `isaac.schema.registered-in` implement points
  1-3 above.

- isaac-agent scenarios that must stay green **unchanged**:
  - `features/config/cli.feature` "config schema --tree expands every named
    sub-schema" (the Try: block also gets appended after `--tree`'s output,
    but this scenario doesn't assert on it).
  - `features/config/cli.feature` "config schema crew renders the map
    wrapper...", "...crew.value prints...", "...providers.key...",
    "...providers.value...", "...crew.value.id...", "...drills into a single
    field...", "...gives a friendly error for an invalid path" (all pure
    generic rendering/drilling, none depend on the hard-coded examples/help
    text or the collection-surfaces set specifically — comms/crew/providers
    are all genuine dynamic-key maps so the generalized slot-id rule still
    covers them).
  - `features/config/cli.feature` "config help lists the schema subcommand".
  - `features/config/schema_cli_options.feature` — all 7 scenarios, **but
    only if** the `:configurable? false` generalization lands (see Wanted
    #3). The `:type` field's `[:registered-in? :isaac.agent/comm [:comms]]`
    validation already exists (see the berth description at
    `isaac-agent/resources/isaac-manifest.edn:92`), so the generic
    `options:` mechanism picks it up for free.

- isaac-agent scenario that needs re-baselining in this same bean:
  - `features/config/cli.feature:478` "config schema prints the root schema
    with title, fields, and guidance". Confirmed by running the real agent
    CLI (`isaac.config.cli.command/run {:root ...} ["schema"]`) against
    isaac-agent HEAD: the full composed root schema's fields, sorted, are
    `attention, bridge, command-paths, comms, crew, defaults, frequencies,
    hot-reload, logging, models, module-registry, modules,
    prefer-entity-files, prompt-dir-names, prompt-paths, providers,
    resource-pools, server, sessions, skill-menu-threshold, skill-paths,
    tools, tz`. Applying the rule: first leaf field = `hot-reload`; first
    dynamic-key table = `comms` (before `crew`, `models`, `modules`,
    `prompt-dir-names`, `providers`, `resource-pools` alphabetically).
    Proposed new scenario body (field-listing assertions unchanged, only the
    Try: lines change):

    ```gherkin
    Scenario: config schema prints the root schema with title, fields, and guidance
      When isaac is run with "config schema"
      Then the stdout matches:
        | pattern                                |
        | \[isaac\] isaac schema                 |
        | crew\s+.*\[crew\]                      |
        | defaults\s+.*\[defaults\]              |
        | models\s+.*\[models\]                  |
        | providers\s+.*\[providers\]            |
        | Try:                                   |
        | isaac config schema hot-reload         |
        | isaac config schema comms              |
        | isaac config schema comms\.value       |
      And the exit code is 0
    ```

## Likely repo scope

- isaac-foundation: `src/isaac/config/cli/schema.clj`,
  `src/isaac/config/schema/term.clj`, `src/isaac/schema/registered_in.clj`,
  delete `src/isaac/config/comm_kinds.clj` + its spec. New feature:
  `features/cli/config_schema.feature`.
- isaac-agent: re-baseline `features/config/cli.feature:478` per above; no
  production code changes expected in isaac-agent itself (its manifest
  already declares `:type` with `[:registered-in? :isaac.agent/comm
  [:comms]]`).
- isaac-server: `src/isaac/http/module.clj`'s `comm-kinds` is the same
  hand-rolled `:configurable?` filter, now redundant once the generic path
  exists in foundation. Out of scope for this bean's acceptance criteria,
  but worth its own follow-up bean (note only, don't file it here).

## Notes

- Accepted by Micah 2026-09-29: `--help` and the live root command show **different** Try: examples
  on an agent-installed CLI (help always shows foundation's generic
  `hot-reload`/`modules`, the live command shows the actually-loaded
  `hot-reload`/`comms`). This falls out of `help-text` fns having no config
  context today. It's not a regression (current `--help` is *also*
  hard-coded and *also* disagrees with what the live command would show if
  it weren't hard-coded too) but it is a visible inconsistency worth Micah
  confirming he's fine with, rather than threading `opts` into every
  subcommand's `--help` to make them match.
- `isaac.config.comm-kinds` and `isaac.http.module/comm-kinds` are
  duplicated today (near-identical code in two repos for the same
  `:isaac.agent/comm` filter). Generalizing into `registered-in?` fixes both
  call sites logically, but only foundation's copy is actually deleted by
  this bean — isaac-server's copy needs its own bean to swap over (it isn't
  a foundation violation the way `isaac.config.cli.schema` is, since
  isaac-server already names `:isaac.agent/comm` explicitly elsewhere; flag
  for follow-up, don't scope-creep this bean into isaac-server).
- The fixture module pair for the new feature (`marigold.cnfs.bridge` /
  `marigold.cnfs.longwave`, written to `/tmp/modules/...` by the feature
  itself) mirrors `features/cli/config_set_namespaced.feature`'s
  `marigold.cgxa.*` fixtures — same shape as the repo's real
  `modules/marigold.bridge` / `modules/marigold.longwave` builtin fixtures,
  but with a unique `.cnfs.` id infix so module discovery's manifest cache
  doesn't confuse the feature's `/tmp` stub with the repo's real builtin
  module of the same id.

feature-baseline: isaac-foundation dc4bc3d7ad07ad5de05619ddcabf054ea334c8c9
feature-baseline: isaac-agent 8bc1e862afe8d9c334b58ed29c3843a873f13837
feature-blob: isaac-foundation features/cli/config_schema.feature 2886689e26b15c710d06ecb9b1a9c6c2b8e8006c
feature-blob: isaac-agent features/config/cli.feature be3696c8660b99880216d3856e8b74a7280d8751 479
