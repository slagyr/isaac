---
# isaac-49zp
title: Any config key may live inline, as <key>.edn, or as <key>/<id>.edn — no kind-specific treatment
status: completed
type: feature
priority: normal
created_at: 2026-09-23T18:01:56Z
updated_at: 2026-09-23T19:31:20Z
---

Repo: **isaac-foundation** (`config/paths.clj`, `loader.clj`, `entities.clj`,
`schema_compose.clj`, `mutate.clj`).

## Why

`~/.isaac/config/isaac.edn` on zanebot has grown large. `:modules` alone is ~40
lines of git coordinates, and it sits in the same file as crews, comms, models
and cron. Splitting a key into its own file is the obvious relief, and the
project already half-does it — but only for kinds a module happens to declare.

## What works today, and what does not

- `config-file?` (`paths.clj:46`) accepts exactly three shapes: `isaac.edn`,
  `<kind>/<id>.edn`, and `(berths|crew|cron|hooks)/<id>.md`.
- A root-level `config/modules.edn` matches none of them. It is **silently
  ignored** — not rejected, not warned about.
- `crew`/`models`/`providers` are *directories* of per-entity files, enabled by
  an `:entity-dir` field the owning module declares in its manifest
  (isaac-agent declares all three). The filename is the entity **id**, not the
  config key.
- `:modules` cannot use that mechanism: `:entity-dir` is declared by config
  slices contributed by modules, but `:modules` is base foundation schema
  (`schema_base.clj:30`) and is the key that tells the loader *which modules to
  load*. A module cannot own the schema for the key that decides whether it
  loads.

## Decision (Micah, 2026-09-23)

**Foundation stops knowing about entity dirs.** The pattern applies globally to
any config key. A directory is a key and an EDN file is a key; no kind gets
special treatment. Concretely, every top-level key may be expressed three ways:

1. inline in `isaac.edn`
2. as `config/<key>.edn` — the file's contents are the value of `:<key>`
3. as `config/<key>/<id>.edn` — each file one entry in the `:<key>` map

All three are equally valid. `:entity-dir` declarations in module manifests
become redundant and should be retired.

This falls out of the generality rather than being designed for: `:crew` may be
one `crew.edn` instead of a directory, and `:modules` may be
`config/modules/isaac.agent.edn` per module. The latter is allowed but not
recommended.

## Two forms for one key is an error, not a precedence

`config/crew.edn` alongside `config/crew/` is refused at load, naming the key
and both paths.

This follows the project's stated rule (ISAAC.md:105-118): *is this a name or a
structure?* Two storage forms for one key is a **structure** disagreement —
"two modules cannot co-own a structure, so a disagreement is an error" — not a
named-entity collision, which is the case that resolves by later-wins + `:warn`.
Failing hard also matches isaac-nq4c's direction of travel: config surprises get
surfaced, not silently resolved.

## Writing values follows the existing pattern, then the preference

`mutate.clj:188` `choose-set-location` already encodes this for entity files:
an existing root path wins, else an existing entity file, else
`:prefer-entity-files`, else root. Extend the same function to the slice form —
do not invent a second convention. If a key already lives in `isaac.edn`,
`config set` updates it there; if it already lives in a file, it updates the
file.

## Hot reload

Any config file update triggers reload. `config-file?` has a single caller
(`change_source_log.cljc:13`), so extending the predicate should cover the
watcher — verify rather than assume.

## Open question: the markdown companions

`markdown-file-pattern` hardcodes `(berths|crew|cron|hooks)/[^/]+\.md` — souls,
ledgers, cron and hook bodies. That is foundation knowing four specific kinds by
name, which the decision above says it should not. It is also the least
generalisable part, since a `.md` companion is meaningful only for kinds that
have a prose field.

Options: derive the set from the schema (a kind declaring a companion field),
accept `<any-key>/<id>.md` uniformly, or leave this one special case explicitly
documented. **Needs a call before implementation.**

## Acceptance

- `config/modules.edn` containing a bare map is loaded as `:modules`, with no
  entry in `isaac.edn`; `isaac config validate` passes and `isaac modules list`
  is unchanged.
- The same works for a key that has no manifest declaration at all, proving the
  mechanism is not kind-specific.
- `config/crew.edn` holding every crew works, and is equivalent to today's
  `config/crew/*.edn` directory.
- A key present both inline and as a file, or both as a file and a directory,
  fails to load with an error naming the key and the conflicting paths.
- `isaac config set <key>.<path> <v>` writes to whichever form already holds the
  key; when the key is absent it honours `:prefer-entity-files`.
- Editing `config/<key>.edn` hot-reloads without a restart.
- Retiring the `:entity-dir` manifest field leaves crew/models/providers
  behaving exactly as before.
- Spec coverage for each shape, the conflict error, and the write routing.

## Design settled (Micah, 2026-09-23)

### Filenames are literal names, never paths

A config filename is one key (or one entity id). Dots in a filename are part of
the name, never separators — even though `.` separates segments in config
*paths* (`paths/split-path-segments`). `config/isaac.agent.edn` is the key
`:isaac.agent`, not `:isaac` → `:agent`. Nesting is expressed by a file's
**contents**, never by its name.

This resolves the ambiguity module ids would otherwise create, and it works
identically at the root and inside a directory, so `modules/isaac.agent.edn` is
the id `isaac.agent` by the same rule.

### A key is a file or a directory, never both

`crew/marvin.edn` alongside `crew/marvin/` is refused at load, naming the key
and both paths. Supporting both as extensions of each other is too flexible —
it is one or the other, at every level. Same rule as `crew.edn` vs `crew/`.

### `_` is the default name inside a directory

`crew/marvin/_.edn` holds marvin's own values (`:model`, `:tools`, `:tags`);
`crew/marvin/soul.md` holds `:soul`.

`_` over `index`: `index` could legitimately be a config key one day, `_`
realistically never will be, and `_` already carries "this is special" in this
tree — the hail config uses `_isaac-template.edn`, `_orchestration-template.edn`,
`_tono-template.edn` for templates.

**`_` means "this map's own values" at every level**, which reads differently
depending on what the level holds and needs no special case:

- `crew/_.edn` — `:crew` is a table of entities, so its own values are a **map
  of names to crew configs**. Several crews in one file, while `crew/keaton.edn`
  holds another; a name appearing in both is the usual duplicate-key error.
- `crew/marvin/_.edn` — `marvin` is one entity, so its own values are marvin's
  **fields** (`:model`, `:tools`, `:tags`).

Note for isaac-h2ck (templating): `_` **exactly** is the map's own values;
`_<name>` is a template. Same prefix, different meaning, distinguished by exact
match — not self-evident, so it is written down rather than inferred.

### Markdown declares its own key, with `_` as the body sentinel

Front matter is ordinary config for the entity. **Exactly one field takes the
value `_`, meaning "this field's value is the markdown body."**

    ---
    model: grover
    tags: [role/worker]
    soul: _
    ---
    You are Marvin, a paranoid android…

So `_` reads the same everywhere: *the default, unnamed one* — as a filename,
this map's own values; as a value, the content below.

Consequences:

- `markdown-file-pattern`'s hardcoded `(berths|crew|cron|hooks)` is retired.
- `companion-md-specs`' hardcoded `{:crew → :soul, :berths → :ledger}`
  (`mutate.clj`) is retired. Foundation stops knowing that crews have souls.
- A `.md` can carry a whole entity — structure and prose — with no paired
  `.edn`.
- It matches existing practice: hail band files already use front matter for
  structured fields plus a body (`isaac-work.md`: `base`, `crew`, then the
  prompt). This only makes the body's destination explicit.

### When front matter is required

| path | key comes from | front matter |
|------|----------------|--------------|
| `crew/marvin/soul.md` | the filename — `soul` | not needed |
| `crew/marvin.md` | cannot be the filename (that is the entity id) | declares the key |

Front matter is required exactly when the filename names the **entity** rather
than the **key**. That falls out of "filenames are literal names" rather than
being a separate rule.

### Rejected: multiple values in one markdown file

A companion `.md` earns its place by being entirely prose — the whole file is
the value, which is why it is pleasant to edit and diff. Dividing it needs a
bespoke section syntax, hence a parser, an escaping story, and a format every
author must learn, reintroducing structure into the one format chosen for having
none. Two prose fields means two files, which costs only a filename. Every prose
field in Isaac today (souls, ledgers, cron and hook bodies) is a single field.

Revisit only if a kind appears with several short prose fields where separate
files are genuinely absurd.

### Open

`_` means both "default filename" and "value is the body". Both read as *the
unnamed one*, so this is believed coherent — but it is an overload, and a
distinct sentinel for the body is the alternative if it grates in practice.

feature-baseline: isaac-foundation 8e7fc97f897bb4b26366fabbec09f59aeabb4da4
feature-blob: isaac-foundation features/cli/config_file_layout.feature 25feccc0bdd54f175e79e25bcc9cb6f4ac363f3a

feature-baseline: isaac-foundation ba7e46085095c5cafc7a104ad8f7cb2d9ec6a4a7
feature-blob: isaac-foundation features/cli/config_file_layout.feature 8eb1b69659563f1a796deb2e239b2f51c1921b74

## Worker note — mechanism done, 5 scenarios blocked on the fixture (2026-09-23)

Branch `bean/isaac-49zp` on isaac-foundation (rebased on `ba7e460`).
`bb ci` green: **1175 specs / 0 failures**, **209 features / 2 failures** — both
failures are the pre-existing `features/cli/modules_pins.feature` stale
`~/.gitlibs` entries, unchanged from `origin/main`.

The mechanism is implemented and proven end-to-end on the real filesystem
(`config/tz.edn` and `config/modules.edn` load; `config set tz` routes to
`config/tz.edn`; `isaac config validate` passes):

- new `isaac.config.tree` — `config/` as a tree of keys; `<key>.edn` slices,
  `<key>/` directories, `_` as a map's own values at every level, markdown
  whose frontmatter field valued `_` takes the body
- `paths/config-file?` tracks the whole tree (the four hardcoded kinds retired)
- `loader` derives its directory set from the filesystem, not from `:entity-dir`
- `entities` reads `<id>/` directories and any frontmatter `.md` as entities
- `mutate/choose-set-location` gained the `:slice` form
- specs: `spec/isaac/config/tree_spec.clj` (21), 6 loader-level examples in
  `load_result_spec`, 4 write-routing examples in `mutate_spec`

**3 of 8 baselined scenarios pass and have `@wip` removed.** The other 5 are
left `@wip`, untouched otherwise, because they cannot pass as written:

1. `a module-declared key may live in its own file`,
   `` `_` inside a directory holds that map's own values ``,
   `an entity may be a directory whose files are its fields`,
   `editing a key's own file is picked up on reload` — all use
   `:kind "parlor"`, whose fields (`loft`, `color`) come from the
   `marigold.comm.parlor` fixture's `:extra-schema`. The scenarios never
   declare that module, and the Background step `the chartroom fixture modules
   are available` only binds the chartroom index. Loading fails with
   `signals[:parlour].kind must be one of ["logbook" "longwave" "parlour"
   "skybeam"]` and `:loft` is pruned as an unknown key.
   Making that step supply the fixture modules was tried and **breaks**
   `features/module/schema_composition.feature` "Without the module declared,
   extended keys are unknown", which asserts exactly the opposite. The other
   scenarios in that file declare the module in `isaac.edn`
   (`:modules {:marigold.comm.parlor {:local/root
   "spec/isaac/config/fixtures/modules/marigold.comm.parlor"}}`); these need
   the same, or a signal kind the chartroom index already declares.

2. `a markdown file named for the entity declares which key its body fills` —
   the frontmatter sets `gauge: helm-mark-iii` but the scenario defines no
   gauges, so `:gauge`'s `[:gauge-exists?]` validation fails with
   `berths.captain.gauge references undefined gauge`. Dropping the `gauge`
   line (locally, not committed) makes the scenario pass, so the `ledger: _`
   mechanism itself is proven.

Planner's call. Worker made no `.feature` edit other than removing `@wip`.

feature-baseline: isaac-foundation 55976d33a46eca665a5b843f5ab8f3978ad7897e
feature-blob: isaac-foundation features/cli/config_file_layout.feature 82ebf539b1e5abf16d1fb47919512f91f60437a8

feature-baseline: isaac-foundation 7dfeed4d7eb542d34ee09fd875ea6321f132d73e
feature-blob: isaac-foundation features/cli/config_file_layout.feature 93c45ba4c383cba9220f434926daac1e31817289

## Landed on main (2026-09-23)

main-sha: isaac-foundation 7b2f0534ccab90e34a49da1531691a66e7857d07

Gate PASS; `bb ci` green (1205 specs / 0 failures, 219 features / 2 failures —
both the pre-existing `modules_pins.feature` stale-`~/.gitlibs` ones, unchanged
from `origin/main`). All 8 baselined scenarios pass with `@wip` removed; no
other `.feature` edit.

What shipped:

- new `isaac.config.tree` — `config/` read as a tree of keys with no
  kind-specific knowledge: `<key>.edn` slices, `<key>/` directories, `_` as a
  map's own values at every level, markdown whose frontmatter field valued `_`
  takes the body. Filenames are literal names (`isaac.agent.edn` → `:isaac.agent`).
- `paths/config-file?` tracks any `.edn`/`.md` at any depth; the hardcoded
  `(berths|crew|cron|hooks)` `markdown-file-pattern` and `entity-file-pattern`
  are gone. Its single caller (`change_source_log.cljc`) is unchanged, so hot
  reload covers every shape.
- `loader` folds slices into root data **before** module discovery, so
  `config/modules.edn` can decide which modules load; directory set now comes
  from the filesystem, not from `:entity-dir`.
- `entities` reads `<id>/` directories as entities whose files are their fields
  and treats any frontmatter `.md` as an entity (no `:frontmatter?` gate).
- `mutate/choose-set-location` gained the `:slice` form (existing home wins →
  `:prefer-entity-files` → `isaac.edn`); `companion-md-specs`' hardcoded
  `{:crew→:soul, :berths→:ledger}` retired in favour of the owning module's
  descriptor.
- A key that is both a file and a directory, or both inline and in its own
  file, is refused at load naming the key and both paths.

### Remaining work (follow-up)

- `:entity-dir` is still **declared** in `src/isaac-manifest.edn` though nothing
  reads it. Removing the declaration would fail
  `features/cli/config_resolution.feature`'s frozen manifest, which sets it.
  Modules can drop the field at leisure.
- The implicit `:companion` path still exists alongside the explicit `_` body
  sentinel. Foundation no longer hardcodes which kinds have prose fields, but
  full retirement is blocked by `features/cli/init.feature`, which freezes
  scaffolded `config/crew/skipper.md` and `config/cron/heartbeat.md` with no
  sentinel in their frontmatter. Note also that
  `companions/resolve-inline-or-md-companion` raises "must be set in .edn OR
  .md" when a companion field appears both ways, so a frontmatter `<companion
  field>: _` is handled by suppressing companion resolution when the sentinel
  fires.
