---
# isaac-h2ck
title: Config templating is hail-only; :_base should let any config entry inherit
status: in-progress
type: feature
priority: normal
created_at: 2026-09-23T18:42:59Z
updated_at: 2026-09-23T19:10:19Z
---

Repo: **isaac-foundation** (config load) — retiring the hail-local copy in
**isaac-hail** (`src/isaac/hail/band_resolve.clj`).

Sibling of isaac-49zp (config file layout). 49zp settles where config *lives*;
this settles how one config entry *inherits* from another. They share the `_`
convention, so whichever lands second honours the first.

## Why

Templating exists, works well, and only hail can use it. `band_resolve.clj`
owns the whole mechanism:

- `template-band?` — "Band files whose names start with `_` are templates —
  inherited from, not hailed"
- `merge-bands` — one-level merge: map-valued keys merge key-wise, scalars and
  vectors replace
- `base` — a band field, resolved at band-resolution time

Nothing else in Isaac can inherit. Crews, models, providers and comms repeat
themselves. On zanebot the crew files duplicate tool allow-lists and directory
grants across seven crews; a change means seven edits.

## Decision (Micah, 2026-09-23)

Templating generalises to **any** config map entity. Using a template is
**explicit** — a config entry declares its own template; nothing is inherited by
proximity or by filename alone.

### The reference: `:_base`

    ;; config/crew/_worker.edn        — a template, not a crew
    {:model "grover" :tools {...} :tags #{:role/worker}}

    ;; config/crew/marvin.edn
    {:_base "_worker" :soul "..."}

- The leading `_` marks the field **reserved**, so it cannot collide with a
  module's legitimate `:base` key — bare `base` is a plausible real config
  field, `_base` is not.
- It matches the convention 49zp settles: seeing `_` means "this is special",
  whether filename, value sentinel, or reserved field.
- The **value** is self-marking too: `"_worker"` starts with `_`, so a reader
  can see it names a template rather than a sibling entity.

### Semantics, generalised from hail's

- A `_`-prefixed entry is **never addressable as a real entity** —
  `template-band?` generalises to any table. A template is not a crew, not a
  model, not a comm.
- Merge is hail's one-level rule: map-valued keys merge key-wise, scalars and
  vectors replace.
- **Single base, not a list.** Multiple inheritance invites ordering questions
  nobody has asked for.
- A template may itself declare `:_base`, so chains work; a cycle is a load
  error naming the cycle.
- The template must be a **sibling in the same map** — `crew/_worker.edn` is a
  template for crews. That is how bands already work (`_tono-template` sits in
  `hail/`).

### The `_` distinction that must be written down

`_` **exactly** is the map's own values (isaac-49zp). `_<name>` is a template.
Same prefix, different meaning, distinguished by exact match. This is workable
but not self-evident, so it belongs in the docs rather than being inferred.

## Migration

- hail's `base:` becomes `:_base`. Small but real: zanebot's band files plus the
  `_*-template` files tracked in `tonotop/planning/orchestration/` and
  `slagyr/orchestration/isaac-beans/`.
- `band_resolve.clj`'s template handling is deleted in favour of the foundation
  mechanism; hail keeps only what is genuinely band-specific.
- Per the order-of-operations rule those repos already carry: edit and commit
  the orchestration trees first, then install to the host.

## Acceptance

- A crew declaring `:_base "_worker"` inherits the template's fields; its own
  keys win; map-valued keys merge key-wise while scalars and vectors replace.
- A `_`-prefixed entry never appears as a real entity — not in
  `isaac crew list`, not as a hailable band, not as a selectable model.
- The same inheritance works for a kind with no manifest declaration at all,
  proving it is not kind-specific.
- A `:_base` naming a missing template is a load error; so is a cycle, naming
  the cycle.
- Hail bands behave exactly as today once migrated to `:_base`, with
  `band_resolve.clj`'s copy removed.
- Spec coverage for merge semantics, chains, cycles, and non-addressability.

feature-baseline: isaac-foundation 8e7fc97f897bb4b26366fabbec09f59aeabb4da4
feature-blob: isaac-foundation features/cli/config_templating.feature c3a13ae44803f5090eff0b902f18cbb438dfb66c

feature-baseline: isaac-foundation ba7e46085095c5cafc7a104ad8f7cb2d9ec6a4a7
feature-blob: isaac-foundation features/cli/config_templating.feature 517659cebad16dcbea7f65edcbad95bdb43d9c02

## Worker note (2026-09-23) — implementation green, contract needs two fixes

Foundation half is implemented and pushed on `bean/isaac-h2ck`
(`isaac-foundation` 1848009). `bb ci` there: spec 1173/0, features 206/2 —
the 2 are the pre-existing `modules_pins.feature` stale-`~/.gitlibs` failures,
unchanged from `origin/main`.

`isaac.config.templating` owns the mechanism; `loader/load-config-result`
resolves `:_base` immediately before `conform-berth-slices`, so templates are
gone before anything validates or instantiates a slot.

All 5 baselined scenarios pass once two things are corrected. Both were
verified by running the corrected text against this branch (5 examples, 0
failures). Neither is editable by a worker.

**1. Scenarios 1–3 need the parlor module declared.** `:loft`, `:color` and
`:mood` reach the `:signals` value-spec only through
`marigold.comm.parlor`'s `:extra-schema`, and `:kind "parlor"` is only
`registered-in?` when that module loads. Without it the load reports
`signals[:parlour].kind must be one of ["logbook" "longwave" "parlour"
"skybeam"]` and prunes `:loft`/`:color`/`:mood` as unknown keys — so
"no validation errors" and `signals.parlour.loft = upper` both fail, for
reasons that have nothing to do with templating. Every scenario in
`features/module/schema_composition.feature` that uses parlor carries the
line; these three dropped it. Each of scenarios 1, 2 and 3 needs it added to
its `isaac.edn`:

    :modules {:marigold.comm.parlor {:local/root "spec/isaac/config/fixtures/modules/marigold.comm.parlor"}}

**2. Scenarios 4–5 expect an error key that cannot be produced.** `:signals`
carries a `:factory` on its value-spec, so it is an open-map reconcile source
and `berths/normalize-error-keys` brackets every per-entry error key under it:
`signals.parlour` is rewritten to `signals[:parlour]` on the way out of the
loader. That is the shape every other error in this table already uses
(`signals[:bert].loft` in `schema_composition.feature`), so it is the natural
shape rather than one bent to fit — producing a literal `signals.parlour`
would mean exempting templating errors from the normalization every other
error goes through. Both rows want:

    | key               | value               |
    | signals[:parlour] | #"(?s).*_missing.*" |

The `#"…"` cells themselves are fine and were kept verbatim: the
`config has validation errors matching:` step did not implement the documented
`features/TABLES.md` matcher dialect, and this branch teaches it to (a step
change, not a feature change).

**Not done: the hail migration.** `band_resolve.clj`'s copy and hail's `base:`
are untouched. Retiring them means rewriting band files that live in the
orchestration trees and on zanebot, which this session is scoped out of, so the
foundation half lands alone. The two mechanisms coexist safely meanwhile —
hail's `apply-to-load-result!` runs first and leaves no `:base` or `_` bands
behind, so the new pass is a no-op on `:hail`.

feature-baseline: isaac-foundation 55976d33a46eca665a5b843f5ab8f3978ad7897e
feature-blob: isaac-foundation features/cli/config_templating.feature 784291683ec896e8710130b864a6be3e12960ac9

## Landed on main (2026-09-23)

main-sha: isaac-foundation 7de29b977678f4ad7055525376bb223e596e5828

Gate PASS on the squash commit. `bb ci`: spec 1173 examples / 0 failures,
features 211 examples / 2 failures — the 2 are the pre-existing
`modules_pins.feature` stale-`~/.gitlibs` failures, identical to `origin/main`
before this bean.

The mechanism lives in `isaac.config.templating`; the loader resolves `:_base`
immediately before `conform-berth-slices`, so a `_<name>` template is gone
before anything validates or instantiates a slot. Resolution is structural
rather than schema-driven, so it works for a key no module declares. `_`
exactly stays isaac-49zp's own-values sentinel and is never a template — the
distinction is documented in `FOUNDATION.md` under Config → Templating rather
than left to be inferred.

Also landed: `config has validation errors matching:` now understands the
documented `features/TABLES.md` `#"…"` matcher cell (full-string, DOTALL),
which it had never implemented. Bare-substring cells are unchanged, so the
other features using that step are unaffected.

### Still open — the hail migration was deliberately left out of scope

`isaac-hail/src/isaac/hail/band_resolve.clj` still owns its own copy
(`template-band?`, `merge-bands`, and the `base` field), and hail bands still
say `base:` rather than `:_base`. Retiring them means rewriting band files that
live in the orchestration trees and on zanebot, which the implementing session
was scoped out of.

The two mechanisms coexist safely meanwhile: hail's `apply-to-load-result!`
runs earlier in the load than the new pass and leaves behind no `:base` field
and no `_`-prefixed bands, so `resolve-config` is a no-op on `:hail`. Once
bands migrate to `:_base`, foundation needs no further change — `:hail` is an
ordinary top-level table to the new code, and `band_resolve.clj`'s copy can be
deleted outright.

### Note for isaac-49zp — `:berths` already resolves its companion implicitly

Raised by the planner and confirmed against this branch. `:berths` declares
`:frontmatter? true` and `:companion {:field :ledger :mode :exclusive}`, and
`companions/resolve-inline-or-md-companion` already fills `:ledger` from
`berths/<id>.md` when the field is absent inline — no sentinel involved. The
same function raises `"must be set in .edn OR .md"` when the field is present
both inline and as a companion file.

That is in tension with isaac-49zp's explicit `ledger: _` sentinel: under
today's exclusive rule a frontmatter `ledger: _` reads as the inline form, so
declaring it alongside the body would trip that error rather than select it.
Whoever implements 49zp has to reconcile the explicit sentinel with the
existing `:companion :exclusive` rule.
