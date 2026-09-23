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
