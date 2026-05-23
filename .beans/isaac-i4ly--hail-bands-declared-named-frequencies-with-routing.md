---
# isaac-i4ly
title: 'Hail bands: declared named frequencies with routing rules'
status: todo
type: feature
created_at: 2026-05-23T21:08:27Z
updated_at: 2026-05-23T21:08:27Z
parent: isaac-ugx7
blocked_by:
    - isaac-wr7d
---

## Motivation

Hail's `:hail/bands` registry holds **bands** — declared named
frequencies with their routing rules (filters, reach, prompt). A
band is to a single hail what a radio station is to a single
transmission: a stable named destination with known listeners,
declared up-front, not discovered per-message.

Hails address bands by name (`{:frequency {:band "bean.ready"}}`),
and the registry resolves the rules. Direct addressing
(`{:frequency {:crew :marvin}}`) bypasses the registry; this bean
is only about bands.

Vocabulary in this slice's design:

- **Frequency** — umbrella concept; what a hail is addressed to.
- **Band** — a declared, named frequency with routing rules in
  `:hail/bands` config.

## Scope

### Bands on disk

Each band is a file under `~/.isaac/config/hail/<band-name>.edn`
with optional `<band-name>.md` companion for the prompt. The
base name IS the band name; dots in band names (`bean.ready`)
parse fine as filenames:

```clojure
;; ~/.isaac/config/hail/bean.ready.edn
{:crew-tags    [:role/worker]
 :session-tags [:project/chess]
 :reach        :one}
```

```
;; ~/.isaac/config/hail/bean.ready.md
A new chess bean is ready. Pick it up, plan your approach,
proceed.
```

### Band schema

Optional addressing fields (at least one required):

- `:crew`         — explicit list of crew ids
- `:crew-tags`    — set of tags crews must carry
- `:session`      — explicit list of session ids
- `:session-tags` — set of tags sessions must carry

Required:

- `:reach`        — `:one` or `:all` (defaults to `:one`)

Combinations form an intersection — all conditions must hold for a
(crew, session) pair to be an eligible listener.

### `isaac.hail.bands` namespace

```clojure
(defprotocol BandRegistry
  (lookup    [this band-name])
  (all-bands [this]))

(deftype HailBands [bands*]
  configurator/Reconfigurable
  (on-startup!       [_ slice] ...)
  (on-config-change! [_ old new] ...))
```

In-memory atom holding `{band-name → declaration}`. Reads
`:hail/bands` config slice (materialized by the existing
file-walker from `~/.isaac/config/hail/*.edn`).

## Out of scope (deferred)

- **Direct addressing** (`:crew`/`:session` on a hail) — fan-out
  worker handles, no registry involvement.
- **Tag matching against actual crews/sessions** — fan-out worker
  filters AFTER band lookup.
- **Mode dispatch / session selection** — fan-out worker.
- **Per-hail overrides of band routing** — declarations are
  contracts; defer.
- **`isaac hail bands list` CLI** — operator inspection; carve as
  follow-up if pain shows up. Use `isaac config get :hail/bands`
  for now.

## Acceptance

- Band files load into the registry at startup.
- File add/remove triggers hot-reload via Reconfigurable.
- Band schema validates each field; bad declarations error with
  the band name and offending field.
- `lookup` returns the declaration or nil.
- `all-bands` returns the full map.
- Existing `isaac config validate` accepts band files (extends
  config schema).

## Verification

**Speclj-primary**, like isaac-pctr. The library code (registry,
schema, Reconfigurable lifecycle) gets unit specs at
`spec/isaac/hail/bands_spec.clj`. One Gherkin scenario added to
`features/config/cli.feature` (or new file) covers "config validate
accepts a band declaration" — exercises the schema integration
end-to-end via the CLI.

**Definition of done:** new Speclj specs green, the new
config-validate scenario green (with `@wip` lifted), and
`bb ci` green.

## Relationship to other beans

- **Parent: isaac-ugx7 (Hail epic).**
- **Blocked by isaac-wr7d (Tagging)** — `:crew-tags` /
  `:session-tags` fields in declarations reference the tag system
  wr7d ships.
- **Companion to isaac-vduq (substrate + CLI).** vduq writes hail
  records that may target bands via `{:frequency {:band "X"}}`;
  this bean lets the fan-out worker resolve those bands at
  delivery time. Substrate itself doesn't depend on bands existing.
- **Used by future fan-out worker bean.**
